package mekanism.common.inventory.container;

import mekanism.common.inventory.slot.SlotEnergy.SlotDischarge;
import mekanism.common.inventory.slot.SlotOutput;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.tile.TileEntityElectricMachine;
import mekanism.common.util.ChargeUtils;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerElectricMachine extends Container
{
	private TileEntityElectricMachine tileEntity;

	public ContainerElectricMachine(InventoryPlayer inventory, TileEntityElectricMachine tentity)
	{
		tileEntity = tentity;
		addSlotToContainer(new Slot(tentity, 0, 56, 17));
		addSlotToContainer(new SlotDischarge(tentity, 1, 56, 53));
		addSlotToContainer(new SlotOutput(tentity, 2, 116, 35));
		int slotX;

		for(slotX = 0; slotX < 3; slotX++)
		{
			for(int slotY = 0; slotY < 9; slotY++)
			{
				addSlotToContainer(new Slot(inventory, slotY + slotX * 9 + 9, 8 + slotY * 18, 84 + slotX * 18));
			}
		}

		for(slotX = 0; slotX < 9; slotX++)
		{
			addSlotToContainer(new Slot(inventory, slotX, 8 + slotX * 18, 142));
		}

		tileEntity.openInventory(inventory.player);
	}

	@Override
	public void onContainerClosed(EntityPlayer entityplayer)
	{
		super.onContainerClosed(entityplayer);

		tileEntity.closeInventory(entityplayer);
	}

	@Override
	public boolean canInteractWith(EntityPlayer entityplayer)
	{
		return tileEntity.isUseableByPlayer(entityplayer);
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotID)
	{
		ItemStack stack = null;
		Slot currentSlot = (Slot)inventorySlots.get(slotID);

		if(currentSlot != null && currentSlot.getHasStack())
		{
			ItemStack slotStack = currentSlot.getStack();
			stack = slotStack.copy();

			if(slotID == 2)
			{
				if(!mergeItemStack(slotStack, 3, inventorySlots.size(), true))
				{
					return null;
				}
			}
			else if(RecipeHandler.getRecipe(new ItemStackInput(slotStack), tileEntity.getRecipes()) != null)
			{
				if(slotID != 0 && slotID != 1 && slotID != 2)
				{
					if(!mergeItemStack(slotStack, 0, 1, false))
					{
						return null;
					}
				}
				else {
					if(!mergeItemStack(slotStack, 3, inventorySlots.size(), true))
					{
						return null;
					}
				}
			}
			else if(ChargeUtils.canBeDischarged(slotStack))
			{
				if(slotID != 1)
				{
					if(!mergeItemStack(slotStack, 1, 2, false))
					{
						return null;
					}
				}
				else if(slotID == 1)
				{
					if(!mergeItemStack(slotStack, 3, inventorySlots.size(), true))
					{
						return null;
					}
				}
			}
			else {
				if(slotID >= 3 && slotID <= 29)
				{
					if(!mergeItemStack(slotStack, 30, inventorySlots.size(), false))
					{
						return null;
					}
				}
				else if(slotID > 29)
				{
					if(!mergeItemStack(slotStack, 3, 29, false))
					{
						return null;
					}
				}
				else {
					if(!mergeItemStack(slotStack, 3, inventorySlots.size(), true))
					{
						return null;
					}
				}
			}

			if(slotStack.stackSize == 0)
			{
				currentSlot.putStack((ItemStack)null);
			}
			else {
				currentSlot.onSlotChanged();
			}

			if(slotStack.stackSize == stack.stackSize)
			{
				return null;
			}

			currentSlot.onPickupFromSlot(player, slotStack);
		}

		return stack;
	}
}
