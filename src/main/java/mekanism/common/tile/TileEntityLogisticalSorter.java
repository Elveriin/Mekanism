package mekanism.common.tile;

import java.util.ArrayList;
import java.util.EnumSet;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.IFilterAccess;
import mekanism.api.Range4D;
import mekanism.common.HashList;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.ISustainedData;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.content.transporter.Finder.FirstFinder;
import mekanism.common.content.transporter.InvStack;
import mekanism.common.content.transporter.TItemStackFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.content.transporter.TransporterManager;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TransporterUtils;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraft.util.EnumFacing;

import io.netty.buffer.ByteBuf;

public class TileEntityLogisticalSorter extends TileEntityElectricBlock implements IRedstoneControl, IActiveState, IFilterAccess, ISustainedData
{
	public HashList<TransporterFilter> filters = new HashList<TransporterFilter>();

	public RedstoneControl controlType = RedstoneControl.DISABLED;

	public EnumColor color;

	public boolean autoEject;

	public boolean roundRobin;

	public int rrIndex = 0;

	public final int MAX_DELAY = 10;

	public int delayTicks;

	public boolean isActive;

	public boolean clientActive;

	public final double ENERGY_PER_ITEM = 5;

	public TileEntityLogisticalSorter()
	{
		super("LogisticalSorter", MachineBlockType.LOGISTICAL_SORTER.baseEnergy);
		inventory = new ItemStack[1];
		doAutoSync = false;
	}

	@Override
	public void onUpdate()
	{
		super.onUpdate();

		if(!worldObj.isRemote)
		{
			delayTicks = Math.max(0, delayTicks-1);

			if(delayTicks == 6)
			{
				setActive(false);
			}

			if(MekanismUtils.canFunction(this) && delayTicks == 0)
			{
				TileEntity back = Coord4D.get(this).offset(EnumFacing.getFront(facing).getOpposite()).getTileEntity(worldObj);
				TileEntity front = Coord4D.get(this).offset(EnumFacing.getFront(facing)).getTileEntity(worldObj);

				if(back instanceof IInventory && (front instanceof ILogisticalTransporter || front instanceof IInventory))
				{
					IInventory inventory = (IInventory)back;

					boolean sentItems = false;
					int min = 0;

					for(TransporterFilter filter : filters)
					{
						InvStack invStack = filter.getStackFromInventory(inventory, EnumFacing.getFront(facing).getOpposite());

						if(invStack != null && invStack.getStack() != null)
						{
							if(filter.canFilter(invStack.getStack()))
							{
								if(filter instanceof TItemStackFilter)
								{
									TItemStackFilter itemFilter = (TItemStackFilter)filter;

									if(itemFilter.sizeMode)
									{
										min = itemFilter.min;
									}
								}
								
								ItemStack used = emitItemToTransporter(front, invStack, filter.color, min);
								
								if(used != null)
								{
									invStack.use(used.stackSize);
									inventory.markDirty();
									setActive(true);
									sentItems = true;
									
									break;
								}
							}
						}
					}

					if(!sentItems && autoEject)
					{
						InvStack invStack = InventoryUtils.takeTopStack(inventory, EnumFacing.getFront(facing).getOpposite().ordinal(), new FirstFinder());
						
						if(invStack != null && invStack.getStack() != null)
						{
							ItemStack used = emitItemToTransporter(front, invStack, color, 0);
							
							if(used != null)
							{
								invStack.use(used.stackSize);
								inventory.markDirty();
								setActive(true);
							}
						}
					}

					delayTicks = 10;
				}
			}

			if(playersUsing.size() > 0)
			{
				for(EntityPlayer player : playersUsing)
				{
					Mekanism.packetHandler.sendTo(new TileEntityMessage(Coord4D.get(this), getGenericPacket(new ArrayList())), (EntityPlayerMP)player);
				}
			}
		}
	}
	
	/*
	 * Returns used
	 */
	public ItemStack emitItemToTransporter(TileEntity front, InvStack inInventory, EnumColor filterColor, int min)
	{
		ItemStack used = null;

		if(front instanceof ILogisticalTransporter)
		{
			ILogisticalTransporter transporter = (ILogisticalTransporter)front;

			if(!roundRobin)
			{
				ItemStack rejects = TransporterUtils.insert(this, transporter, inInventory.getStack(), filterColor, true, min);

				if(TransporterManager.didEmit(inInventory.getStack(), rejects))
				{
					used = TransporterManager.getToUse(inInventory.getStack(), rejects);
				}
			}
			else {
				ItemStack rejects = TransporterUtils.insertRR(this, transporter, inInventory.getStack(), filterColor, true, min);

				if(TransporterManager.didEmit(inInventory.getStack(), rejects))
				{
					used = TransporterManager.getToUse(inInventory.getStack(), rejects);
				}
			}
		}
		else if(front instanceof IInventory)
		{
			ItemStack rejects = InventoryUtils.putStackInInventory((IInventory)front, inInventory.getStack(), facing, false);

			if(TransporterManager.didEmit(inInventory.getStack(), rejects))
			{
				used = TransporterManager.getToUse(inInventory.getStack(), rejects);
			}
		}
		
		return used;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTags)
	{
		super.writeToNBT(nbtTags);

		nbtTags.setInteger("controlType", controlType.ordinal());

		if(color != null)
		{
			nbtTags.setInteger("color", TransporterUtils.colors.indexOf(color));
		}

		nbtTags.setBoolean("autoEject", autoEject);
		nbtTags.setBoolean("roundRobin", roundRobin);

		nbtTags.setInteger("rrIndex", rrIndex);

		NBTTagList filterTags = new NBTTagList();

		for(TransporterFilter filter : filters)
		{
			NBTTagCompound tagCompound = new NBTTagCompound();
			filter.write(tagCompound);
			filterTags.appendTag(tagCompound);
		}

		if(filterTags.tagCount() != 0)
		{
			nbtTags.setTag("filters", filterTags);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTags)
	{
		super.readFromNBT(nbtTags);

		controlType = RedstoneControl.values()[nbtTags.getInteger("controlType")];

		if(nbtTags.hasKey("color"))
		{
			color = TransporterUtils.colors.get(nbtTags.getInteger("color"));
		}

		autoEject = nbtTags.getBoolean("autoEject");
		roundRobin = nbtTags.getBoolean("roundRobin");

		rrIndex = nbtTags.getInteger("rrIndex");

		if(nbtTags.hasKey("filters"))
		{
			NBTTagList tagList = nbtTags.getTagList("filters", NBT.TAG_COMPOUND);

			for(int i = 0; i < tagList.tagCount(); i++)
			{
				filters.add(TransporterFilter.readFromNBT((NBTTagCompound)tagList.getCompoundTagAt(i)));
			}
		}
	}

	@Override
	public void handlePacketData(ByteBuf dataStream)
	{
		if(!worldObj.isRemote)
		{
			int type = dataStream.readInt();

			if(type == 0)
			{
				int clickType = dataStream.readInt();

				if(clickType == 0)
				{
					color = TransporterUtils.increment(color);
				}
				else if(clickType == 1)
				{
					color = TransporterUtils.decrement(color);
				}
				else if(clickType == 2)
				{
					color = null;
				}
			}
			else if(type == 1)
			{
				autoEject = !autoEject;
			}
			else if(type == 2)
			{
				roundRobin = !roundRobin;
				rrIndex = 0;
			}

			return;
		}

		super.handlePacketData(dataStream);

		int type = dataStream.readInt();

		if(type == 0)
		{
			isActive = dataStream.readBoolean();
			controlType = RedstoneControl.values()[dataStream.readInt()];

			int c = dataStream.readInt();

			if(c != -1)
			{
				color = TransporterUtils.colors.get(c);
			}
			else {
				color = null;
			}

			autoEject = dataStream.readBoolean();
			roundRobin = dataStream.readBoolean();

			filters.clear();

			int amount = dataStream.readInt();

			for(int i = 0; i < amount; i++)
			{
				filters.add(TransporterFilter.readFromPacket(dataStream));
			}

			MekanismUtils.updateBlock(worldObj, xCoord, yCoord, zCoord);
		}
		else if(type == 1)
		{
			isActive = dataStream.readBoolean();
			controlType = RedstoneControl.values()[dataStream.readInt()];

			int c = dataStream.readInt();

			if(c != -1)
			{
				color = TransporterUtils.colors.get(c);
			}
			else {
				color = null;
			}

			autoEject = dataStream.readBoolean();
			roundRobin = dataStream.readBoolean();

			MekanismUtils.updateBlock(worldObj, xCoord, yCoord, zCoord);
		}
		else if(type == 2)
		{
			filters.clear();

			int amount = dataStream.readInt();

			for(int i = 0; i < amount; i++)
			{
				filters.add(TransporterFilter.readFromPacket(dataStream));
			}
		}
	}

	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);

		data.add(0);

		data.add(isActive);
		data.add(controlType.ordinal());

		if(color != null)
		{
			data.add(TransporterUtils.colors.indexOf(color));
		}
		else {
			data.add(-1);
		}

		data.add(autoEject);
		data.add(roundRobin);

		data.add(filters.size());

		for(TransporterFilter filter : filters)
		{
			filter.write(data);
		}

		return data;
	}

	public ArrayList getGenericPacket(ArrayList data)
	{
		super.getNetworkedData(data);

		data.add(1);

		data.add(isActive);
		data.add(controlType.ordinal());

		if(color != null)
		{
			data.add(TransporterUtils.colors.indexOf(color));
		}
		else {
			data.add(-1);
		}

		data.add(autoEject);
		data.add(roundRobin);

		return data;

	}

	public ArrayList getFilterPacket(ArrayList data)
	{
		super.getNetworkedData(data);

		data.add(2);

		data.add(filters.size());

		for(TransporterFilter filter : filters)
		{
			filter.write(data);
		}

		return data;
	}

	public boolean canSendHome(ItemStack stack)
	{
		TileEntity back = Coord4D.get(this).offset(EnumFacing.getFront(facing).getOpposite()).getTileEntity(worldObj);

		if(back instanceof IInventory)
		{
			return InventoryUtils.canInsert(back, null, stack, EnumFacing.getFront(facing).getOpposite().ordinal(), true);
		}

		return false;
	}

	public boolean hasInventory()
	{
		return Coord4D.get(this).offset(EnumFacing.getFront(facing).getOpposite()).getTileEntity(worldObj) instanceof IInventory;
	}

	public ItemStack sendHome(ItemStack stack)
	{
		TileEntity back = Coord4D.get(this).offset(EnumFacing.getFront(facing).getOpposite()).getTileEntity(worldObj);

		if(back instanceof IInventory)
		{
			return InventoryUtils.putStackInInventory((IInventory)back, stack, EnumFacing.getFront(facing).getOpposite().ordinal(), true);
		}

		return stack;
	}

	@Override
	public boolean canExtractItem(int slotID, ItemStack itemstack, EnumFacing side)
	{
		return false;
	}

	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
	{
		return false;
	}

	@Override
	public int getInventoryStackLimit()
	{
		return 1;
	}

	@Override
	public int[] getSlotsForFace(EnumFacing side)
	{
		if(side == EnumFacing.getFront(facing).ordinal() || side == EnumFacing.getFront(facing).getOpposite().ordinal())
		{
			return new int[] {0};
		}

		return InventoryUtils.EMPTY;
	}

	@Override
	public void openInventory()
	{
		if(!worldObj.isRemote)
		{
			Mekanism.packetHandler.sendToReceivers(new TileEntityMessage(Coord4D.get(this), getFilterPacket(new ArrayList())), new Range4D(Coord4D.get(this)));
		}
	}

	@Override
	public RedstoneControl getControlType()
	{
		return controlType;
	}

	@Override
	public void setControlType(RedstoneControl type)
	{
		controlType = type;
	}

	@Override
	public boolean canPulse()
	{
		return true;
	}

	@Override
	public void setActive(boolean active)
	{
		isActive = active;

		if(clientActive != active)
		{
			Mekanism.packetHandler.sendToReceivers(new TileEntityMessage(Coord4D.get(this), getNetworkedData(new ArrayList())), new Range4D(Coord4D.get(this)));

			if(active)
			{
				worldObj.playSoundEffect(xCoord, yCoord, zCoord, "mekanism:etc.Click", 0.3F, 1);
			}

			clientActive = active;
		}
	}

	@Override
	public boolean getActive()
	{
		return isActive;
	}

	@Override
	public boolean renderUpdate()
	{
		return true;
	}

	@Override
	public boolean lightUpdate()
	{
		return false;
	}

	@Override
	protected EnumSet<EnumFacing> getConsumingSides()
	{
		return EnumSet.noneOf(EnumFacing.class);
	}

	@Override
	public boolean canSetFacing(int facing)
	{
		return true;
	}

	@Override
	public NBTTagCompound getFilterData(NBTTagCompound nbtTags)
	{
		nbtTags.setInteger("controlType", controlType.ordinal());

		if(color != null)
		{
			nbtTags.setInteger("color", TransporterUtils.colors.indexOf(color));
		}

		nbtTags.setBoolean("autoEject", autoEject);
		nbtTags.setBoolean("roundRobin", roundRobin);

		nbtTags.setInteger("rrIndex", rrIndex);

		NBTTagList filterTags = new NBTTagList();

		for(TransporterFilter filter : filters)
		{
			NBTTagCompound tagCompound = new NBTTagCompound();
			filter.write(tagCompound);
			filterTags.appendTag(tagCompound);
		}

		if(filterTags.tagCount() != 0)
		{
			nbtTags.setTag("filters", filterTags);
		}
		
		return nbtTags;
	}

	@Override
	public void setFilterData(NBTTagCompound nbtTags)
	{
		controlType = RedstoneControl.values()[nbtTags.getInteger("controlType")];

		if(nbtTags.hasKey("color"))
		{
			color = TransporterUtils.colors.get(nbtTags.getInteger("color"));
		}

		autoEject = nbtTags.getBoolean("autoEject");
		roundRobin = nbtTags.getBoolean("roundRobin");

		rrIndex = nbtTags.getInteger("rrIndex");

		if(nbtTags.hasKey("filters"))
		{
			NBTTagList tagList = nbtTags.getTagList("filters", NBT.TAG_COMPOUND);

			for(int i = 0; i < tagList.tagCount(); i++)
			{
				filters.add(TransporterFilter.readFromNBT((NBTTagCompound)tagList.getCompoundTagAt(i)));
			}
		}
	}

	@Override
	public String getDataType()
	{
		return "tooltip.filterCard.logisticalSorter";
	}

	@Override
	public void writeSustainedData(ItemStack itemStack) 
	{
		itemStack.getTagCompound().setBoolean("hasSorterConfig", true);

		if(color != null)
		{
			itemStack.getTagCompound().setInteger("color", TransporterUtils.colors.indexOf(color));
		}

		itemStack.getTagCompound().setBoolean("autoEject", autoEject);
		itemStack.getTagCompound().setBoolean("roundRobin", roundRobin);

		NBTTagList filterTags = new NBTTagList();

		for(TransporterFilter filter : filters)
		{
			NBTTagCompound tagCompound = new NBTTagCompound();
			filter.write(tagCompound);
			filterTags.appendTag(tagCompound);
		}

		if(filterTags.tagCount() != 0)
		{
			itemStack.getTagCompound().setTag("filters", filterTags);
		}
	}

	@Override
	public void readSustainedData(ItemStack itemStack) 
	{
		if(itemStack.getTagCompound().hasKey("hasSorterConfig"))
		{
			if(itemStack.getTagCompound().hasKey("color"))
			{
				color = TransporterUtils.colors.get(itemStack.getTagCompound().getInteger("color"));
			}

			autoEject = itemStack.getTagCompound().getBoolean("autoEject");
			roundRobin = itemStack.getTagCompound().getBoolean("roundRobin");

			if(itemStack.getTagCompound().hasKey("filters"))
			{
				NBTTagList tagList = itemStack.getTagCompound().getTagList("filters", NBT.TAG_COMPOUND);

				for(int i = 0; i < tagList.tagCount(); i++)
				{
					filters.add(TransporterFilter.readFromNBT((NBTTagCompound)tagList.getCompoundTagAt(i)));
				}
			}
		}
	}
}
