/** 
 * Copyright (c) Krapht, 2011
 * 
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public 
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package logisticspipes.proxy.buildcraft.bc60;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

import logisticspipes.LPConstants;
import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.LogisticsSolidBlock;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.pipes.PipeItemsFluidSupplier;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.pipes.basic.LogisticsBlockGenericPipe;
import logisticspipes.pipes.basic.LogisticsBlockGenericPipe.Part;
import logisticspipes.pipes.basic.LogisticsBlockGenericPipe.RaytraceResult;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.VersionNotSupportedException;
import logisticspipes.proxy.buildcraft.BCPipeInformationProvider;
import logisticspipes.proxy.buildcraft.LPRoutedBCTravelingItem;
import logisticspipes.proxy.buildcraft.bc60.gates.ActionDisableLogistics;
import logisticspipes.proxy.buildcraft.bc60.gates.LogisticsTriggerProvider;
import logisticspipes.proxy.buildcraft.bc60.gates.TriggerCrafting;
import logisticspipes.proxy.buildcraft.bc60.gates.TriggerHasDestination;
import logisticspipes.proxy.buildcraft.bc60.gates.TriggerNeedsPower;
import logisticspipes.proxy.buildcraft.bc60.gates.TriggerSupplierFailed;
import logisticspipes.proxy.buildcraft.bc60.recipeproviders.AssemblyTable;
import logisticspipes.proxy.buildcraft.bc60.renderer.FacadeMatrix;
import logisticspipes.proxy.buildcraft.bc60.renderer.FacadeRenderHelper;
import logisticspipes.proxy.buildcraft.bc60.subproxies.BCCoreState;
import logisticspipes.proxy.buildcraft.bc60.subproxies.BCPipePart;
import logisticspipes.proxy.buildcraft.bc60.subproxies.BCRenderState;
import logisticspipes.proxy.buildcraft.bc60.subproxies.BCTilePart;
import logisticspipes.proxy.buildcraft.bc60.subproxies.LPBCPowerProxy;
import logisticspipes.proxy.buildcraft.subproxies.IBCCoreState;
import logisticspipes.proxy.buildcraft.subproxies.IBCPipePart;
import logisticspipes.proxy.buildcraft.subproxies.IBCRenderState;
import logisticspipes.proxy.buildcraft.subproxies.IBCTilePart;
import logisticspipes.proxy.buildcraft.subproxies.ILPBCPowerProxy;
import logisticspipes.proxy.interfaces.IBCProxy;
import logisticspipes.proxy.interfaces.ICraftingParts;
import logisticspipes.proxy.interfaces.ICraftingRecipeProvider;
import logisticspipes.recipes.CraftingDependency;
import logisticspipes.recipes.RecipeManager;
import logisticspipes.renderer.state.PipeRenderState;
import logisticspipes.transport.LPTravelingItem;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.transport.PipeFluidTransportLogistics;
import logisticspipes.utils.MatrixTranformations;
import logisticspipes.utils.tuples.LPPosition;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import buildcraft.BuildCraftEnergy;
import buildcraft.BuildCraftSilicon;
import buildcraft.BuildCraftTransport;
import buildcraft.api.core.BCLog;
import buildcraft.api.gates.ActionManager;
import buildcraft.api.gates.IAction;
import buildcraft.api.gates.IGateExpansion;
import buildcraft.api.gates.ITrigger;
import buildcraft.api.mj.IBatteryObject;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.power.IPowerReceptor;
import buildcraft.api.power.PowerHandler;
import buildcraft.api.power.PowerHandler.PowerReceiver;
import buildcraft.api.transport.IPipeConnection;
import buildcraft.api.transport.IPipeConnection.ConnectOverride;
import buildcraft.api.transport.IPipeTile;
import buildcraft.api.transport.IPipeTile.PipeType;
import buildcraft.api.transport.PipeWire;
import buildcraft.core.CoreConstants;
import buildcraft.core.IMachine;
import buildcraft.core.ITileBufferHolder;
import buildcraft.core.ItemRobot;
import buildcraft.core.render.RenderEntityBlock;
import buildcraft.core.render.RenderEntityBlock.RenderInfo;
import buildcraft.core.robots.AIDocked;
import buildcraft.core.robots.EntityRobot;
import buildcraft.transport.BlockGenericPipe;
import buildcraft.transport.Gate;
import buildcraft.transport.ItemFacade;
import buildcraft.transport.ItemPlug;
import buildcraft.transport.ItemRobotStation;
import buildcraft.transport.Pipe;
import buildcraft.transport.PipeIconProvider;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TileGenericPipe;
import buildcraft.transport.TravelingItem;
import buildcraft.transport.gates.ItemGate;
import buildcraft.transport.render.PipeRendererTESR;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BuildCraftProxy implements IBCProxy {
	
	public static ITrigger LogisticsFailedTrigger;
	public static ITrigger LogisticsCraftingTrigger;
	public static ITrigger LogisticsNeedPowerTrigger;
	public static ITrigger LogisticsHasDestinationTrigger;
	public static IAction LogisticsDisableAction;
	
	private Method canPipeConnect;

	public PipeType logisticsPipeType;
	public PipeType wrapperPipeType;
	
	public BuildCraftProxy() {
		String BCVersion = null;
		try {
			Field versionField = buildcraft.core.Version.class.getDeclaredField("VERSION");
			BCVersion = (String) versionField.get(null);
		} catch(Exception e) {
			e.printStackTrace();
		}
		if(BCVersion != null) {
			if(!BCVersion.equals("@VERSION@") && !BCVersion.contains("6.0.18") && !BCVersion.contains("6.0.17")) {
				throw new VersionNotSupportedException("BC", BCVersion, "6.0.17 - 6.0.18", "");
			}
		} else {
			LogisticsPipes.log.info("Couldn't check the BC Version.");
		}
	}

	public void initProxy() {
		try {
			canPipeConnect = TileGenericPipe.class.getDeclaredMethod("canPipeConnect", new Class[]{TileEntity.class, ForgeDirection.class});
			canPipeConnect.setAccessible(true);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean canPipeConnect(TileEntity pipe, TileEntity with, ForgeDirection side) {
		if(canPipeConnect == null) {
			initProxy();
		}
		if(!(pipe instanceof TileGenericPipe)) throw new IllegalArgumentException();
		try {
			return (Boolean) canPipeConnect.invoke(pipe, with, side);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public void registerTrigger() {
		ActionManager.registerTriggerProvider(new LogisticsTriggerProvider());
		
		/* Triggers */
		LogisticsFailedTrigger = new TriggerSupplierFailed();
		LogisticsNeedPowerTrigger = new TriggerNeedsPower();
		LogisticsCraftingTrigger = new TriggerCrafting();
		LogisticsHasDestinationTrigger = new TriggerHasDestination();
		
		/* Actions */
		LogisticsDisableAction = new ActionDisableLogistics();
	}
	
	public void resetItemRotation() {
		try {
			Object renderer = TileEntityRendererDispatcher.instance.mapSpecialRenderers.get(TileGenericPipe.class);
			Field f = PipeRendererTESR.class.getDeclaredField("dummyEntityItem");
			f.setAccessible(true);
			EntityItem item = (EntityItem) f.get(renderer);
			item.hoverStart = 0;
		} catch(NoSuchFieldException e) {
			e.printStackTrace();
		} catch(SecurityException e) {
			e.printStackTrace();
		} catch(IllegalArgumentException e) {
			e.printStackTrace();
		} catch(IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	public void registerPipeInformationProvider() {
		SimpleServiceLocator.pipeInformaitonManager.registerProvider(TileGenericPipe.class, BCPipeInformationProvider.class);
	}
	
	public boolean insertIntoBuildcraftPipe(TileEntity tile, LPTravelingItem item) {
		if(tile instanceof TileGenericPipe) {
			TileGenericPipe pipe = (TileGenericPipe)tile;
			if(BlockGenericPipe.isValid(pipe.pipe) && pipe.pipe.transport instanceof PipeTransportItems) {
				TravelingItem bcItem = null;
				if(item instanceof LPTravelingItemServer) {
					LPRoutedBCTravelingItem lpBCItem = new LPRoutedBCTravelingItem();
					lpBCItem.setRoutingInformation(((LPTravelingItemServer)item).getInfo());
					lpBCItem.saveToExtraNBTData();
					bcItem = lpBCItem;
				} else {
					//TODO is this needed ClientSide ?
					//bcItem = TravelingItem.make();
					return true;
				}
				LPPosition p = new LPPosition(tile.xCoord + 0.5F, tile.yCoord + CoreConstants.PIPE_MIN_POS, tile.zCoord + 0.5F);
				if(item.output.getOpposite() == ForgeDirection.DOWN) {
					p.moveForward(item.output.getOpposite(), 0.24F);
				} else if(item.output.getOpposite() == ForgeDirection.UP) {
					p.moveForward(item.output.getOpposite(), 0.74F);
				} else {
					p.moveForward(item.output.getOpposite(), 0.49F);
				}
				bcItem.setPosition(p.getXD(), p.getYD(), p.getZD());
				bcItem.setSpeed(item.getSpeed());
				if(item.getItemIdentifierStack() != null) {
					bcItem.setItemStack(item.getItemIdentifierStack().makeNormalStack());
				}
				((PipeTransportItems)pipe.pipe.transport).injectItem(bcItem, item.output);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isIPipeTile(TileEntity tile) {
		return tile instanceof IPipeTile;
	}

	@Override
	public boolean checkForPipeConnection(TileEntity with, ForgeDirection side, LogisticsTileGenericPipe pipe) {
		if (with instanceof TileGenericPipe) {
			if (((TileGenericPipe) with).hasPlug(side.getOpposite()))
				return false;
			Pipe otherPipe = ((TileGenericPipe) with).pipe;

			if (!BlockGenericPipe.isValid(otherPipe))
				return false;
			
			if(!(otherPipe.transport instanceof PipeTransportItems))
				return false;
		}
		return true;
	}

	@Override
	public boolean checkConnectionOverride(TileEntity with, ForgeDirection side, LogisticsTileGenericPipe pipe) {
		if (with instanceof IPipeConnection) {
			IPipeConnection.ConnectOverride override = ((IPipeConnection) with).overridePipeConnection(PipeType.ITEM, side.getOpposite());
			if(override == IPipeConnection.ConnectOverride.DISCONNECT) {
				//if it doesn't don't want to connect to item pipes, how about fluids?
				if(pipe.pipe.transport instanceof PipeFluidTransportLogistics || pipe.pipe instanceof PipeItemsFluidSupplier) {
					override = ((IPipeConnection) with).overridePipeConnection(PipeType.FLUID, side.getOpposite());
				}
				if(override == IPipeConnection.ConnectOverride.DISCONNECT) {
					//nope, maybe you'd like some BC power?
					if(pipe.getCPipe().getUpgradeManager().hasBCPowerSupplierUpgrade()) {
						override = ((IPipeConnection) with).overridePipeConnection(PipeType.POWER, side.getOpposite());
					}
				}
			}
			if (override == IPipeConnection.ConnectOverride.DISCONNECT)
				return false;
		}
		return true;
	}

	@Override
	public boolean isMachineManagingSolids(TileEntity tile) {
		return tile instanceof IMachine && ((IMachine)tile).manageSolids();
	}

	@Override
	public boolean isMachineManagingFluids(TileEntity tile) {
		return tile instanceof IMachine && ((IMachine) tile).manageFluids();
	}
	
	@Override
	public boolean handleBCClickOnPipe(ItemStack currentItem, CoreUnroutedPipe pipe, World world, int x, int y, int z, EntityPlayer player, int side, LogisticsBlockGenericPipe block) {
		if(PipeWire.RED.isPipeWire(currentItem)) {
			if(addOrStripWire(player, pipe, PipeWire.RED)) { return true; }
		} else if(PipeWire.BLUE.isPipeWire(currentItem)) {
			if(addOrStripWire(player, pipe, PipeWire.BLUE)) { return true; }
		} else if(PipeWire.GREEN.isPipeWire(currentItem)) {
			if(addOrStripWire(player, pipe, PipeWire.GREEN)) { return true; }
		} else if(PipeWire.YELLOW.isPipeWire(currentItem)) {
			if(addOrStripWire(player, pipe, PipeWire.YELLOW)) { return true; }
		} else if(currentItem.getItem() instanceof ItemGate) {
			if(addOrStripGate(world, x, y, z, player, pipe, block)) { return true; }
		} else if(currentItem.getItem() instanceof ItemPlug) {
			if(addOrStripPlug(world, x, y, z, player, ForgeDirection.getOrientation(side), pipe, block)) { return true; }
		} else if(currentItem.getItem() instanceof ItemRobotStation) {
			if(addOrStripRobotStation(world, x, y, z, player, ForgeDirection.getOrientation(side), pipe, block)) { return true; }
		} else if(currentItem.getItem() instanceof ItemFacade) {
			if(addOrStripFacade(world, x, y, z, player, ForgeDirection.getOrientation(side), pipe, block)) { return true; }
		} else if(currentItem.getItem() instanceof ItemRobot) {
			if(!world.isRemote) {
				RaytraceResult rayTraceResult = block.doRayTrace(world, x, y, z, player);
				
				if(rayTraceResult.hitPart == Part.RobotStation) {
					EntityRobot robot = ((ItemRobot)currentItem.getItem()).createRobot(world);
					
					float px = x + 0.5F + rayTraceResult.sideHit.offsetX * 0.5F;
					float py = y + 0.5F + rayTraceResult.sideHit.offsetY * 0.5F;
					float pz = z + 0.5F + rayTraceResult.sideHit.offsetZ * 0.5F;
					
					robot.setPosition(px, py, pz);
					
					//robot.setDockingStation(pipe.container, rayTraceResult.sideHit);
					robot.dockingStation.x = pipe.container.xCoord;
					robot.dockingStation.y = pipe.container.yCoord;
					robot.dockingStation.z = pipe.container.zCoord;
					robot.dockingStation.side = rayTraceResult.sideHit;
					
					robot.currentAI = new AIDocked();
					world.spawnEntityInWorld(robot);
					
					if(!player.capabilities.isCreativeMode) {
						player.getCurrentEquippedItem().stackSize--;
					}
					
					return true;
				}
			}
		}
		return false;
	}
	
	private boolean addOrStripGate(World world, int x, int y, int z, EntityPlayer player, CoreUnroutedPipe pipe, LogisticsBlockGenericPipe block) {
		if(addGate(player, pipe)) { return true; }
		if(player.isSneaking()) {
			RaytraceResult rayTraceResult = block.doRayTrace(world, x, y, z, player);
			if(rayTraceResult != null && rayTraceResult.hitPart == Part.Gate) {
				if(stripGate(pipe)) { return true; }
			}
		}
		return false;
	}
	
	private boolean addGate(EntityPlayer player, CoreUnroutedPipe pipe) {
		if(!pipe.hasGate()) {
			pipe.bcPipePart.makeGate(pipe, player.getCurrentEquippedItem());
			if(!player.capabilities.isCreativeMode) {
				player.getCurrentEquippedItem().splitStack(1);
			}
			pipe.container.scheduleRenderUpdate();
			return true;
		}
		return false;
	}
	
	private boolean stripGate(CoreUnroutedPipe pipe) {
		if(pipe.hasGate()) {
			if(!pipe.container.getWorldObj().isRemote) {
				((Gate)pipe.bcPipePart.getGate(0)).dropGate();
			}
			pipe.resetGate();
			return true;
		}
		return false;
	}
	
	private boolean addOrStripWire(EntityPlayer player, CoreUnroutedPipe pipe, PipeWire color) {
		if(addWire(pipe, color)) {
			if(!player.capabilities.isCreativeMode) {
				player.getCurrentEquippedItem().splitStack(1);
			}
			return true;
		}
		return player.isSneaking() && stripWire(pipe, color);
	}
	
	private boolean addWire(CoreUnroutedPipe pipe, PipeWire color) {
		if(!pipe.bcPipePart.getWireSet()[color.ordinal()]) {
			pipe.bcPipePart.getWireSet()[color.ordinal()] = true;
			pipe.bcPipePart.getSignalStrength()[color.ordinal()] = 0;
			pipe.container.scheduleNeighborChange();
			return true;
		}
		return false;
	}
	
	private boolean stripWire(CoreUnroutedPipe pipe, PipeWire color) {
		if(pipe.bcPipePart.getWireSet()[color.ordinal()]) {
			if(!pipe.container.getWorldObj().isRemote) {
				dropWire(color, pipe);
			}
			pipe.bcPipePart.getWireSet()[color.ordinal()] = false;
			pipe.container.scheduleRenderUpdate();
			return true;
		}
		return false;
	}
	
	private boolean addOrStripFacade(World world, int x, int y, int z, EntityPlayer player, ForgeDirection side, CoreUnroutedPipe pipe, LogisticsBlockGenericPipe block) {
		RaytraceResult rayTraceResult = block.doRayTrace(world, x, y, z, player);
		if(player.isSneaking()) {
			if(rayTraceResult != null && rayTraceResult.hitPart == Part.Facade) {
				if(stripFacade(pipe, rayTraceResult.sideHit)) { return true; }
			}
		}
		if(rayTraceResult != null && (rayTraceResult.hitPart != Part.Facade)) {
			if(addFacade(player, pipe, rayTraceResult.sideHit != null && rayTraceResult.sideHit != ForgeDirection.UNKNOWN ? rayTraceResult.sideHit : side)) { return true; }
		}
		return false;
	}
	
	private boolean addFacade(EntityPlayer player, CoreUnroutedPipe pipe, ForgeDirection side) {
		ItemStack stack = player.getCurrentEquippedItem();
		if(stack != null && stack.getItem() instanceof ItemFacade && pipe.container.tilePart.addFacade(side, ItemFacade.getType(stack), ItemFacade.getWireType(stack), ItemFacade.getBlocks(stack), ItemFacade.getMetaValues(stack))) {
			if(!player.capabilities.isCreativeMode) {
				stack.stackSize--;
			}
			return true;
		}
		return false;
	}
	
	private boolean stripFacade(CoreUnroutedPipe pipe, ForgeDirection side) {
		return pipe.container.tilePart.dropFacade(side);
	}
	
	private boolean addOrStripPlug(World world, int x, int y, int z, EntityPlayer player, ForgeDirection side, CoreUnroutedPipe pipe, LogisticsBlockGenericPipe block) {
		RaytraceResult rayTraceResult = block.doRayTrace(world, x, y, z, player);
		if(player.isSneaking()) {
			if(rayTraceResult != null && rayTraceResult.hitPart == Part.Plug) {
				if(stripPlug(pipe, rayTraceResult.sideHit)) { return true; }
			}
		}
		if(rayTraceResult != null && (rayTraceResult.hitPart == Part.Pipe || rayTraceResult.hitPart == Part.Gate)) {
			if(addPlug(player, pipe, rayTraceResult.sideHit != null && rayTraceResult.sideHit != ForgeDirection.UNKNOWN ? rayTraceResult.sideHit : side)) { return true; }
		}
		return false;
	}
	
	private boolean addOrStripRobotStation(World world, int x, int y, int z, EntityPlayer player, ForgeDirection side, CoreUnroutedPipe pipe, LogisticsBlockGenericPipe block) {
		RaytraceResult rayTraceResult = block.doRayTrace(world, x, y, z, player);
		if(player.isSneaking()) {
			if(rayTraceResult != null && rayTraceResult.hitPart == Part.RobotStation) {
				if(stripRobotStation(pipe, rayTraceResult.sideHit)) { return true; }
			}
		}
		if(rayTraceResult != null && (rayTraceResult.hitPart == Part.Pipe || rayTraceResult.hitPart == Part.Gate)) {
			if(addRobotStation(player, pipe, rayTraceResult.sideHit != null && rayTraceResult.sideHit != ForgeDirection.UNKNOWN ? rayTraceResult.sideHit : side)) { return true; }
		}
		return false;
	}
	
	private boolean addPlug(EntityPlayer player, CoreUnroutedPipe pipe, ForgeDirection side) {
		ItemStack stack = player.getCurrentEquippedItem();
		if(pipe.container.tilePart.addPlug(side)) {
			if(!player.capabilities.isCreativeMode) {
				stack.stackSize--;
			}
			return true;
		}
		return false;
	}
	
	private boolean addRobotStation(EntityPlayer player, CoreUnroutedPipe pipe, ForgeDirection side) {
		ItemStack stack = player.getCurrentEquippedItem();
		if(pipe.container.tilePart.addRobotStation(side)) {
			if(!player.capabilities.isCreativeMode) {
				stack.stackSize--;
			}
			return true;
		}
		return false;
	}
	
	private boolean stripPlug(CoreUnroutedPipe pipe, ForgeDirection side) {
		return pipe.container.tilePart.removeAndDropPlug(side);
	}
	
	private boolean stripRobotStation(CoreUnroutedPipe pipe, ForgeDirection side) {
		return pipe.container.tilePart.removeAndDropPlug(side);
	}
	
	@Override
	public boolean stripEquipment(World world, int x, int y, int z, EntityPlayer player, CoreUnroutedPipe pipe, LogisticsBlockGenericPipe block) {
		// Try to strip facades first
		RaytraceResult rayTraceResult = block.doRayTrace(world, x, y, z, player);
		if(rayTraceResult != null && rayTraceResult.hitPart == Part.Facade) {
			if(stripFacade(pipe, rayTraceResult.sideHit)) { return true; }
		}
		
		// Try to strip wires second, starting with yellow.
		for(PipeWire color: PipeWire.values()) {
			if(stripWire(pipe, color)) { return true; }
		}
		
		return stripGate(pipe);
	}
	
	/**
	 * Drops a pipe wire item of the passed color.
	 *
	 * @param pipeWire
	 */
	private void dropWire(PipeWire pipeWire, CoreUnroutedPipe pipe) {
		pipe.dropItem(pipeWire.getStack());
	}
	
	@Override
	public IBCPipePart getBCPipePart(LogisticsTileGenericPipe tile) {
		return new BCPipePart(tile);
	}

	@Override
	public IBCTilePart getBCTilePart(LogisticsTileGenericPipe tile) {
		return new BCTilePart(tile);
	}
	
	@Override
	public ItemStack getPipePlugItemStack() {
		return new ItemStack(BuildCraftTransport.plugItem);
	}
	
	@Override
	public ItemStack getRobotStationItemStack() {
		return new ItemStack(BuildCraftTransport.robotStationItem);
	}

	@Override
	public void notifyOfChange(LogisticsTileGenericPipe pipe, TileEntity tile, ForgeDirection o) {
		if (tile instanceof ITileBufferHolder) {
			((ITileBufferHolder) tile).blockCreated(o, BuildCraftTransport.genericPipeBlock, pipe);
		}
		if (tile instanceof TileGenericPipe) {
			((TileGenericPipe) tile).scheduleNeighborChange();
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void renderGatesWires(LogisticsTileGenericPipe pipe, double x, double y, double z) {
		BCRenderState bcRenderState = (BCRenderState) pipe.renderState.bcRenderState.getOriginal();

		if (bcRenderState.wireMatrix.hasWire(PipeWire.RED)) {
			pipeWireRender(pipe, LPConstants.BC_PIPE_MIN_POS, LPConstants.BC_PIPE_MAX_POS, LPConstants.BC_PIPE_MIN_POS, PipeWire.RED, x, y, z);
		}

		if (bcRenderState.wireMatrix.hasWire(PipeWire.BLUE)) {
			pipeWireRender(pipe, LPConstants.BC_PIPE_MAX_POS, LPConstants.BC_PIPE_MAX_POS, LPConstants.BC_PIPE_MAX_POS, PipeWire.BLUE, x, y, z);
		}

		if (bcRenderState.wireMatrix.hasWire(PipeWire.GREEN)) {
			pipeWireRender(pipe, LPConstants.BC_PIPE_MAX_POS, LPConstants.BC_PIPE_MIN_POS, LPConstants.BC_PIPE_MIN_POS, PipeWire.GREEN, x, y, z);
		}

		if (bcRenderState.wireMatrix.hasWire(PipeWire.YELLOW)) {
			pipeWireRender(pipe, LPConstants.BC_PIPE_MIN_POS, LPConstants.BC_PIPE_MIN_POS, LPConstants.BC_PIPE_MAX_POS, PipeWire.YELLOW, x, y, z);
		}

		if (pipe.pipe.hasGate()) {
			pipeGateRender(pipe, x, y, z);
		}
	}

	private void pipeWireRender(LogisticsTileGenericPipe pipe, float cx, float cy, float cz, PipeWire color, double x, double y, double z) {

		PipeRenderState state = pipe.renderState;
		BCRenderState bcRenderState = (BCRenderState) state.bcRenderState.getOriginal();
		
		float minX = LPConstants.BC_PIPE_MIN_POS;
		float minY = LPConstants.BC_PIPE_MIN_POS;
		float minZ = LPConstants.BC_PIPE_MIN_POS;

		float maxX = LPConstants.BC_PIPE_MAX_POS;
		float maxY = LPConstants.BC_PIPE_MAX_POS;
		float maxZ = LPConstants.BC_PIPE_MAX_POS;

		boolean foundX = false, foundY = false, foundZ = false;

		if (bcRenderState.wireMatrix.isWireConnected(color, ForgeDirection.WEST)) {
			minX = 0;
			foundX = true;
		}

		if (bcRenderState.wireMatrix.isWireConnected(color, ForgeDirection.EAST)) {
			maxX = 1;
			foundX = true;
		}

		if (bcRenderState.wireMatrix.isWireConnected(color, ForgeDirection.DOWN)) {
			minY = 0;
			foundY = true;
		}

		if (bcRenderState.wireMatrix.isWireConnected(color, ForgeDirection.UP)) {
			maxY = 1;
			foundY = true;
		}

		if (bcRenderState.wireMatrix.isWireConnected(color, ForgeDirection.NORTH)) {
			minZ = 0;
			foundZ = true;
		}

		if (bcRenderState.wireMatrix.isWireConnected(color, ForgeDirection.SOUTH)) {
			maxZ = 1;
			foundZ = true;
		}

		boolean center = false;

		if (minX == 0 && maxX != 1 && (foundY || foundZ)) {
			if (cx == LPConstants.BC_PIPE_MIN_POS) {
				maxX = LPConstants.BC_PIPE_MIN_POS;
			} else {
				center = true;
			}
		}

		if (minX != 0 && maxX == 1 && (foundY || foundZ)) {
			if (cx == LPConstants.BC_PIPE_MAX_POS) {
				minX = LPConstants.BC_PIPE_MAX_POS;
			} else {
				center = true;
			}
		}

		if (minY == 0 && maxY != 1 && (foundX || foundZ)) {
			if (cy == LPConstants.BC_PIPE_MIN_POS) {
				maxY = LPConstants.BC_PIPE_MIN_POS;
			} else {
				center = true;
			}
		}

		if (minY != 0 && maxY == 1 && (foundX || foundZ)) {
			if (cy == LPConstants.BC_PIPE_MAX_POS) {
				minY = LPConstants.BC_PIPE_MAX_POS;
			} else {
				center = true;
			}
		}

		if (minZ == 0 && maxZ != 1 && (foundX || foundY)) {
			if (cz == LPConstants.BC_PIPE_MIN_POS) {
				maxZ = LPConstants.BC_PIPE_MIN_POS;
			} else {
				center = true;
			}
		}

		if (minZ != 0 && maxZ == 1 && (foundX || foundY)) {
			if (cz == LPConstants.BC_PIPE_MAX_POS) {
				minZ = LPConstants.BC_PIPE_MAX_POS;
			} else {
				center = true;
			}
		}

		boolean found = foundX || foundY || foundZ;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);
		RenderHelper.disableStandardItemLighting();

		GL11.glColor3f(1, 1, 1);
		GL11.glTranslatef((float) x, (float) y, (float) z);

		float scale = 1.001f;
		GL11.glTranslatef(0.5F, 0.5F, 0.5F);
		GL11.glScalef(scale, scale, scale);
		GL11.glTranslatef(-0.5F, -0.5F, -0.5F);


		bindTexture(TextureMap.locationBlocksTexture);

		RenderInfo renderBox = new RenderInfo();
		renderBox.texture = BuildCraftTransport.instance.wireIconProvider.getIcon(bcRenderState.wireMatrix.getWireIconIndex(color));

		// Z render

		if (minZ != LPConstants.BC_PIPE_MIN_POS || maxZ != LPConstants.BC_PIPE_MAX_POS || !found) {
			renderBox.setBounds(cx == LPConstants.BC_PIPE_MIN_POS ? cx - 0.05F : cx, cy == LPConstants.BC_PIPE_MIN_POS ? cy - 0.05F : cy, minZ, cx == LPConstants.BC_PIPE_MIN_POS ? cx
					: cx + 0.05F, cy == LPConstants.BC_PIPE_MIN_POS ? cy : cy + 0.05F, maxZ);
			RenderEntityBlock.INSTANCE.renderBlock(renderBox, pipe.getWorldObj(), 0, 0, 0, pipe.xCoord, pipe.yCoord, pipe.zCoord, true, true);
		}

		// X render

		if (minX != LPConstants.BC_PIPE_MIN_POS || maxX != LPConstants.BC_PIPE_MAX_POS || !found) {
			renderBox.setBounds(minX, cy == LPConstants.BC_PIPE_MIN_POS ? cy - 0.05F : cy, cz == LPConstants.BC_PIPE_MIN_POS ? cz - 0.05F : cz, maxX, cy == LPConstants.BC_PIPE_MIN_POS ? cy
					: cy + 0.05F, cz == LPConstants.BC_PIPE_MIN_POS ? cz : cz + 0.05F);
			RenderEntityBlock.INSTANCE.renderBlock(renderBox, pipe.getWorldObj(), 0, 0, 0, pipe.xCoord, pipe.yCoord, pipe.zCoord, true, true);
		}

		// Y render

		if (minY != LPConstants.BC_PIPE_MIN_POS || maxY != LPConstants.BC_PIPE_MAX_POS || !found) {
			renderBox.setBounds(cx == LPConstants.BC_PIPE_MIN_POS ? cx - 0.05F : cx, minY, cz == LPConstants.BC_PIPE_MIN_POS ? cz - 0.05F : cz, cx == LPConstants.BC_PIPE_MIN_POS ? cx
					: cx + 0.05F, maxY, cz == LPConstants.BC_PIPE_MIN_POS ? cz : cz + 0.05F);
			RenderEntityBlock.INSTANCE.renderBlock(renderBox, pipe.getWorldObj(), 0, 0, 0, pipe.xCoord, pipe.yCoord, pipe.zCoord, true, true);
		}

		if (center || !found) {
			renderBox.setBounds(cx == LPConstants.BC_PIPE_MIN_POS ? cx - 0.05F : cx, cy == LPConstants.BC_PIPE_MIN_POS ? cy - 0.05F : cy, cz == LPConstants.BC_PIPE_MIN_POS ? cz - 0.05F : cz,
					cx == LPConstants.BC_PIPE_MIN_POS ? cx : cx + 0.05F, cy == LPConstants.BC_PIPE_MIN_POS ? cy : cy + 0.05F, cz == LPConstants.BC_PIPE_MIN_POS ? cz : cz + 0.05F);
			RenderEntityBlock.INSTANCE.renderBlock(renderBox, pipe.getWorldObj(), 0, 0, 0, pipe.xCoord, pipe.yCoord, pipe.zCoord, true, true);
		}

		RenderHelper.enableStandardItemLighting();

		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void pipeGateRender(LogisticsTileGenericPipe pipe, double x, double y, double z) {
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
//		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);
//		GL11.glDisable(GL11.GL_TEXTURE_2D);
		RenderHelper.disableStandardItemLighting();

		GL11.glColor3f(1, 1, 1);
		GL11.glTranslatef((float) x, (float) y, (float) z);

		bindTexture(TextureMap.locationBlocksTexture);

		IIcon iconLogic;
		if (pipe.renderState.bcRenderState.isGateLit()) {
			iconLogic = ((Gate)pipe.pipe.bcPipePart.getGate(0)).logic.getIconLit();
		} else {
			iconLogic = ((Gate)pipe.pipe.bcPipePart.getGate(0)).logic.getIconDark();
		}

		float translateCenter = 0;

		// Render base gate
		renderGate(pipe, iconLogic, 0, 0.1F, 0, 0);

		float pulseStage = ((Gate)pipe.pipe.bcPipePart.getGate(0)).getPulseStage() * 2F;

		if (pipe.renderState.bcRenderState.isGatePulsing() || pulseStage != 0) {
			// Render pulsing gate
			float amplitude = 0.10F;
			float start = 0.01F;

			if (pulseStage < 1) {
				translateCenter = (pulseStage * amplitude) + start;
			} else {
				translateCenter = amplitude - ((pulseStage - 1F) * amplitude) + start;
			}

			renderGate(pipe, iconLogic, 0, 0.13F, translateCenter, translateCenter);
		}

		IIcon materialIcon = ((Gate)pipe.pipe.bcPipePart.getGate(0)).material.getIconBlock();
		if (materialIcon != null) {
			renderGate(pipe, materialIcon, 1, 0.13F, translateCenter, translateCenter);
		}

		for (IGateExpansion expansion : ((Gate)pipe.pipe.bcPipePart.getGate(0)).expansions.keySet()) {
			renderGate(pipe, expansion.getOverlayBlock(), 2, 0.13F, translateCenter, translateCenter);
		}

		RenderHelper.enableStandardItemLighting();

		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderGate(LogisticsTileGenericPipe tile, IIcon icon, int layer, float trim, float translateCenter, float extraDepth) {
		PipeRenderState state = tile.renderState;
		BCRenderState bcRenderState = (BCRenderState) state.bcRenderState.getOriginal();
		
		RenderInfo renderBox = new RenderInfo();
		renderBox.texture = icon;

		float[][] zeroState = new float[3][2];
		float min = LPConstants.BC_PIPE_MIN_POS + trim / 2F;
		float max = LPConstants.BC_PIPE_MAX_POS - trim / 2F;

		// X START - END
		zeroState[0][0] = min;
		zeroState[0][1] = max;
		// Y START - END
		zeroState[1][0] = LPConstants.BC_PIPE_MIN_POS - 0.10F - 0.001F * layer;
		zeroState[1][1] = LPConstants.BC_PIPE_MIN_POS + 0.001F + 0.01F * layer + extraDepth;
		// Z START - END
		zeroState[2][0] = min;
		zeroState[2][1] = max;

		for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			if (shouldRenderNormalPipeSide(state, bcRenderState, direction)) {
				GL11.glPushMatrix();

				float xt = direction.offsetX * translateCenter,
						yt = direction.offsetY * translateCenter,
						zt = direction.offsetZ * translateCenter;

				GL11.glTranslatef(xt, yt, zt);

				float[][] rotated = MatrixTranformations.deepClone(zeroState);
				MatrixTranformations.transform(rotated, direction);

				if (layer != 0) {
					renderBox.setRenderSingleSide(direction.ordinal());
				}
				renderBox.setBounds(rotated[0][0], rotated[1][0], rotated[2][0], rotated[0][1], rotated[1][1], rotated[2][1]);
				RenderEntityBlock.INSTANCE.renderBlock(renderBox, tile.getWorldObj(), 0, 0, 0, tile.xCoord, tile.yCoord, tile.zCoord, true, true);
				GL11.glPopMatrix();
			}
		}
	}
	
	private boolean shouldRenderNormalPipeSide(PipeRenderState state, BCRenderState bcRenderState, ForgeDirection direction) {
		return !state.pipeConnectionMatrix.isConnected(direction) && bcRenderState.facadeMatrix.getFacadeBlock(direction) == null && !bcRenderState.plugMatrix.isConnected(direction) && !bcRenderState.robotStationMatrix.isConnected(direction) && !isOpenOrientation(state, direction);
	}
	
	private boolean isOpenOrientation(PipeRenderState state, ForgeDirection direction) {
		int connections = 0;
		
		ForgeDirection targetOrientation = ForgeDirection.UNKNOWN;
		
		for(ForgeDirection o: ForgeDirection.VALID_DIRECTIONS) {
			if(state.pipeConnectionMatrix.isConnected(o)) {
				
				connections++;
				
				if(connections == 1) {
					targetOrientation = o;
				}
			}
		}
		
		if(connections > 1 || connections == 0) { return false; }
		
		return targetOrientation.getOpposite() == direction;
	}

	private void bindTexture(ResourceLocation p_147499_1_) {
		TextureManager texturemanager = TileEntityRendererDispatcher.instance.field_147553_e;
		if(texturemanager != null) {
			texturemanager.bindTexture(p_147499_1_);
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void pipeFacadeRenderer(RenderBlocks renderblocks, LogisticsBlockGenericPipe block, PipeRenderState state, int x, int y, int z) {
		FacadeRenderHelper.pipeFacadeRenderer(renderblocks, block, state, x, y, z);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void pipePlugRenderer(RenderBlocks renderblocks, Block block, PipeRenderState state, int x, int y, int z) {
		
		BCRenderState bcRenderState = (BCRenderState)state.bcRenderState.getOriginal();
		
		float zFightOffset = 1F / 4096F;

		float[][] zeroState = new float[3][2];
		// X START - END
		zeroState[0][0] = 0.25F + zFightOffset;
		zeroState[0][1] = 0.75F - zFightOffset;
		// Y START - END
		zeroState[1][0] = 0.125F;
		zeroState[1][1] = 0.251F;
		// Z START - END
		zeroState[2][0] = 0.25F + zFightOffset;
		zeroState[2][1] = 0.75F - zFightOffset;

		state.currentTexture = BuildCraftTransport.instance.pipeIconProvider.getIcon(PipeIconProvider.TYPE.PipeStructureCobblestone.ordinal()); // Structure Pipe

		for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			if (bcRenderState.plugMatrix.isConnected(direction)) {
				float[][] rotated = MatrixTranformations.deepClone(zeroState);
				MatrixTranformations.transform(rotated, direction);

				renderblocks.setRenderBounds(rotated[0][0], rotated[1][0], rotated[2][0], rotated[0][1], rotated[1][1], rotated[2][1]);
				renderblocks.renderStandardBlock(block, x, y, z);
			}
		}

		// X START - END
		zeroState[0][0] = 0.25F + 0.125F / 2 + zFightOffset;
		zeroState[0][1] = 0.75F - 0.125F / 2 + zFightOffset;
		// Y START - END
		zeroState[1][0] = 0.25F;
		zeroState[1][1] = 0.25F + 0.125F;
		// Z START - END
		zeroState[2][0] = 0.25F + 0.125F / 2;
		zeroState[2][1] = 0.75F - 0.125F / 2;

		state.currentTexture = BuildCraftTransport.instance.pipeIconProvider.getIcon(PipeIconProvider.TYPE.PipeStructureCobblestone.ordinal()); // Structure Pipe

		for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			if (bcRenderState.plugMatrix.isConnected(direction)) {
				float[][] rotated = MatrixTranformations.deepClone(zeroState);
				MatrixTranformations.transform(rotated, direction);

				renderblocks.setRenderBounds(rotated[0][0], rotated[1][0], rotated[2][0], rotated[0][1], rotated[1][1], rotated[2][1]);
				renderblocks.renderStandardBlock(block, x, y, z);
			}
		}

	}

	@Override
	public ItemStack getDropFacade(CoreUnroutedPipe pipe, ForgeDirection dir) {
		FacadeMatrix matrix = ((BCRenderState)pipe.container.renderState.bcRenderState.getOriginal()).facadeMatrix;
		Block block = matrix.getFacadeBlock(dir);
		if (block != null) {
			return ItemFacade.getFacade(block,matrix.getFacadeMetaId(dir));
		}
		return null;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void pipeRobotStationRenderer(RenderBlocks renderblocks, LogisticsBlockGenericPipe block, PipeRenderState state, int x, int y, int z) {
		float width = 0.075F;

		pipeRobotStationPartRender (renderblocks, block, state, x, y, z,
				0.45F, 0.55F,
				0.0F, 0.224F,
				0.45F, 0.55F);


		/*pipeRobotStationPartRender (renderblocks, block, state, x, y, z,
				0.25F, 0.75F,
				0.025F, 0.224F,
				0.25F, 0.25F + width);

		pipeRobotStationPartRender (renderblocks, block, state, x, y, z,
				0.25F, 0.75F,
				0.025F, 0.224F,
				0.75F - width, 0.75F);

		pipeRobotStationPartRender (renderblocks, block, state, x, y, z,
				0.25F, 0.25F + width,
				0.025F, 0.224F,
				0.25F + width, 0.75F - width);

		pipeRobotStationPartRender (renderblocks, block, state, x, y, z,
				0.75F - width, 0.75F,
				0.025F, 0.224F,
				0.25F + width, 0.75F - width);*/

		float zFightOffset = 1F / 4096F;

		float[][] zeroState = new float[3][2];


		// X START - END
		zeroState[0][0] = 0.25F + zFightOffset;
		zeroState[0][1] = 0.75F - zFightOffset;
		// Y START - END
		zeroState[1][0] = 0.225F;
		zeroState[1][1] = 0.251F;
		// Z START - END
		zeroState[2][0] = 0.25F + zFightOffset;
		zeroState[2][1] = 0.75F - zFightOffset;

		state.currentTexture = BuildCraftTransport.instance.pipeIconProvider
				.getIcon(PipeIconProvider.TYPE.PipeRobotStation.ordinal()); // Structure
																			// Pipe

		for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			if (((BCRenderState)state.bcRenderState.getOriginal()).robotStationMatrix.isConnected(direction)) {
				float[][] rotated = MatrixTranformations.deepClone(zeroState);
				MatrixTranformations.transform(rotated, direction);

				renderblocks.setRenderBounds(rotated[0][0], rotated[1][0],
						rotated[2][0], rotated[0][1], rotated[1][1],
						rotated[2][1]);
				renderblocks.renderStandardBlock(block, x, y, z);
			}
		}
	}

	private void pipeRobotStationPartRender(RenderBlocks renderblocks,
			Block block, PipeRenderState state, int x, int y, int z,
			float xStart, float xEnd, float yStart, float yEnd, float zStart,
			float zEnd) {

		float zFightOffset = 1F / 4096F;

		float[][] zeroState = new float[3][2];
		// X START - END
		zeroState[0][0] = xStart + zFightOffset;
		zeroState[0][1] = xEnd - zFightOffset;
		// Y START - END
		zeroState[1][0] = yStart;
		zeroState[1][1] = yEnd;
		// Z START - END
		zeroState[2][0] = zStart + zFightOffset;
		zeroState[2][1] = zEnd - zFightOffset;

		state.currentTexture = BuildCraftTransport.instance.pipeIconProvider
				.getIcon(PipeIconProvider.TYPE.PipeRobotStation.ordinal()); // Structure
																			// Pipe

		for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			if (((BCRenderState)state.bcRenderState.getOriginal()).robotStationMatrix.isConnected(direction)) {
				float[][] rotated = MatrixTranformations.deepClone(zeroState);
				MatrixTranformations.transform(rotated, direction);

				renderblocks.setRenderBounds(rotated[0][0], rotated[1][0],
						rotated[2][0], rotated[0][1], rotated[1][1],
						rotated[2][1]);
				renderblocks.renderStandardBlock(block, x, y, z);
			}
		}

	}

	@Override
	public boolean isActive() {
		return true;
	}

	@Override
	public boolean isInstalled() {
		return true;
	}

	@Override
	public PipeType getLPPipeType() {
		if(logisticsPipeType == null) {
			logisticsPipeType = net.minecraftforge.common.util.EnumHelper.addEnum(PipeType.class, "LOGISTICS", new Class<?>[]{}, new Object[]{});
			wrapperPipeType = net.minecraftforge.common.util.EnumHelper.addEnum(PipeType.class, "WRAPPED-LOGISTICS", new Class<?>[]{}, new Object[]{});
		}
		return logisticsPipeType;
	}

	@Override
	public ILPBCPowerProxy getPowerReceiver(TileEntity tile, ForgeDirection orientation) {
		if(tile == null) return null;
		PowerReceiver receptor = null;
		if(tile instanceof IPowerReceptor) {
			receptor = ((IPowerReceptor)tile).getPowerReceiver(orientation.getOpposite());
		}
		final World world = tile.getWorldObj();
		IBatteryObject battery = MjAPI.getMjBattery(tile, MjAPI.DEFAULT_POWER_FRAMEWORK, orientation.getOpposite());
		if(battery != null) {
			receptor = new PowerHandler(new IPowerReceptor() {
				@Override public World getWorld() {return world;}
				@Override public PowerReceiver getPowerReceiver(ForgeDirection paramForgeDirection) {return null;}
				@Override public void doWork(PowerHandler paramPowerHandler) {}
			}, PowerHandler.Type.MACHINE, battery).getPowerReceiver();
		}
		if(receptor == null) return null;
		return new LPBCPowerProxy(receptor);
	}

	@Override
	public ICraftingParts getRecipeParts() {
		return new ICraftingParts() {
			@Override
			public ItemStack getChipTear1() {
				return new ItemStack(BuildCraftSilicon.redstoneChipset, 1, 1);
			}

			@Override
			public ItemStack getChipTear2() {
				return new ItemStack(BuildCraftSilicon.redstoneChipset, 1, 2);
			}

			@Override
			public ItemStack getChipTear3() {
				return new ItemStack(BuildCraftSilicon.redstoneChipset, 1, 3);
			}

			@Override
			public Object getGearTear1() {
				return "gearIron";
			}

			@Override
			public Object getGearTear2() {
				return "gearGold";
			}

			@Override
			public Object getGearTear3() {
				return "gearDiamond";
			}

			@Override
			public Object getSortingLogic() {
				return BuildCraftTransport.pipeItemsDiamond;
			}

			@Override
			public Object getBasicTransport() {
				return BuildCraftTransport.pipeItemsCobblestone;
			}

			@Override
			public Object getWaterProof() {
				return BuildCraftTransport.pipeWaterproof;
			}

			@Override
			public Object getExtractorItem() {
				return BuildCraftTransport.pipeItemsWood;
			}

			@Override
			public Object getExtractorFluid() {
				return BuildCraftTransport.pipeFluidsWood;
			}
		};
	}

	@Override
	public void addCraftingRecipes(ICraftingParts parts) {
		RecipeManager.craftingManager.addRecipe(new ItemStack(LogisticsPipes.LogisticsSolidBlock, 1, LogisticsSolidBlock.LOGISTICS_BC_POWERPROVIDER), CraftingDependency.Power_Distribution, new Object[] { 
			false, 
			"PEP", 
			"CTC", 
			"PGP", 
			Character.valueOf('C'), parts.getChipTear1(),
			Character.valueOf('G'), parts.getChipTear2(),
			Character.valueOf('E'), new ItemStack(BuildCraftEnergy.engineBlock, 1, 1), 
			Character.valueOf('T'), Blocks.redstone_block, 
			Character.valueOf('P'), Items.paper
		});
		
		RecipeManager.craftingManager.addRecipe(new ItemStack(LogisticsPipes.UpgradeItem, 1, ItemUpgrade.POWER_BC_SUPPLIER), CraftingDependency.Power_Distribution, new Object[] { 
			false, 
			"PEP", 
			"CTC", 
			"PGP", 
			Character.valueOf('C'), parts.getChipTear1(),
			Character.valueOf('G'), parts.getChipTear2(),
			Character.valueOf('E'), new ItemStack(BuildCraftEnergy.engineBlock, 1, 1), 
			Character.valueOf('T'), new ItemStack(LogisticsPipes.UpgradeItem, 1, ItemUpgrade.POWER_TRANSPORTATION), 
			Character.valueOf('P'), Items.paper
		});
	}

	@Override
	public Object overridePipeConnection(LogisticsTileGenericPipe pipe, Object type, ForgeDirection dir) {
		TileEntity target = pipe.getTile(dir, true);
		ConnectOverride result = ConnectOverride.DEFAULT;
		if(LogisticsBlockGenericPipe.isFullyDefined(pipe.pipe) && target instanceof TileGenericPipe && !pipe.tilePart.hasPlug(dir) && !pipe.tilePart.hasRobotStation(dir)) {
			result = ConnectOverride.CONNECT;
		}
		return result;
	}

	@Override
	public IBCCoreState getBCCoreState() {
		return new BCCoreState();
	}

	@Override
	public IBCRenderState getBCRenderState() {
		return new BCRenderState();
	}

	@Override
	public void checkUpdateNeighbour(TileEntity tile) {}

	@Override
	public void logWarning(String msg) {
		BCLog.logger.log(Level.WARNING, msg);
	}

	@Override
	public Class<? extends ICraftingRecipeProvider> getAssemblyTableProviderClass() {
		return AssemblyTable.class;
	}

	@Override
	public boolean isTileGenericPipe(TileEntity tile) {
		return tile instanceof TileGenericPipe;
	}
}
