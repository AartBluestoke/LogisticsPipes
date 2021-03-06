package logisticspipes.gui.orderer;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.orderer.RequestLiquidOrdererRefreshPacket;
import logisticspipes.network.packets.orderer.SubmitLiquidRequestPacket;
import logisticspipes.pipes.PipeLiquidRequestLogistics;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.ItemIdentifier;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;

public class LiquidGuiOrderer extends GuiOrderer {

	public LiquidGuiOrderer(PipeLiquidRequestLogistics pipe, EntityPlayer entityPlayer) {
		super(pipe.xCoord, pipe.yCoord, pipe.zCoord, MainProxy.getDimensionForWorld(pipe.worldObj), entityPlayer);
		refreshItems();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initGui() {
		super.initGui();
		buttonList.add(new GuiButton(3, guiLeft + 10, bottom - 25, 46, 20, "Refresh")); // Refresh
	}
	
	@Override
	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.id == 0 && selectedItem != null){
			if(editsearch) {
				editsearchb = false;
			}
			clickWasButton = true;
//TODO 		MainProxy.sendPacketToServer(new PacketRequestSubmit( NetworkConstants.LIQUID_REQUEST_SUBMIT, xCoord,yCoord,zCoord,dimension,selectedItem.getItem(),requestCount).getPacket());
			MainProxy.sendPacketToServer(PacketHandler.getPacket(SubmitLiquidRequestPacket.class).setDimension(dimension).setStack(selectedItem.getItem().makeStack(requestCount)).setPosX(xCoord).setPosY(yCoord).setPosZ(zCoord));
			refreshItems();
		} else {
			super.actionPerformed(guibutton);
		}
	}
	
	@Override
	protected int getAmountChangeMode(int step) {
		if(step == 1) {
			return 1;
		} else if(step == 2) {
			return 1000;
		} else if(step == 4) {
			return 100;
		} else {
			return 16000;
		}
	}
	
	@Override
	protected boolean isShiftPageChange() {
		return false;
	}
	
	@Override
	protected int getStackAmount() {
		return 1000;
	}
	
	@Override
	public void refreshItems() {
//TODO 	MainProxy.sendPacketToServer(new PacketPipeInteger(NetworkConstants.ORDERER_LIQUID_REFRESH_REQUEST, xCoord, yCoord, zCoord, dimension).getPacket());		
		MainProxy.sendPacketToServer(PacketHandler.getPacket(RequestLiquidOrdererRefreshPacket.class).setInteger(dimension).setPosX(xCoord).setPosY(yCoord).setPosZ(zCoord));
	}

	@Override
	public void specialItemRendering(ItemIdentifier item, int x, int y) {}
}
