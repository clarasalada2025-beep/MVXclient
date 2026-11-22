package net.minecraft.client.mvx;

import net.lax1dude.eaglercraft.v1_8.Keyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

/**
 * MVX Aimbot (teste) — mira suavizada no jogador mais próximo da mira.
 *
 * Liga/desliga com a tecla R (só com nenhuma tela aberta).
 * Chamado uma vez por tick a partir de EntityPlayerSP.onUpdate(), logo depois de super.onUpdate():
 * assim prevRotationYaw/Pitch guardam o valor antigo e a câmera interpola sem tranco.
 *
 * Use apenas em singleplayer ou em servidores de teste seus, onde isso é permitido.
 */
public class MVXAimbot {

	public static boolean enabled = false;
	public static int toggleKey = Keyboard.KEY_R;

	/** true = só mira enquanto o botão de ataque (clique esquerdo) está pressionado. */
	public static boolean onlyWhileAttacking = true;
	/** alcance máximo em blocos. */
	public static float range = 5.0F;
	/** ângulo máximo (graus) entre a mira atual e o alvo para ele ser considerado. */
	public static float fov = 90.0F;
	/** fração da diferença corrigida por tick: 1.0 = trava na hora, valores baixos = mais suave. */
	public static float smooth = 0.35F;
	/** limite de giro por tick, em graus. */
	public static float maxStep = 25.0F;
	/** ponto de mira no corpo: 0.0 = pés, 1.0 = topo da cabeça. */
	public static float aimHeight = 0.85F;
	/** antecipa o movimento do alvo (em ticks). 0 = desligado. */
	public static float predictTicks = 0.0F;
	/** exige linha de visão livre até o alvo. */
	public static boolean requireLineOfSight = true;

	private static boolean keyWasDown = false;

	public static void onTick(Minecraft mc, EntityPlayerSP me) {
		if (mc.currentScreen == null) {
			boolean down = Keyboard.isKeyDown(toggleKey);
			if (down && !keyWasDown) {
				enabled = !enabled;
				me.addChatMessage(new ChatComponentText("[MVX] Aimbot " + (enabled ? "ON" : "OFF")));
			}
			keyWasDown = down;
		} else {
			keyWasDown = false;
		}

		if (!enabled || mc.currentScreen != null || mc.theWorld == null) {
			return;
		}
		if (onlyWhileAttacking && !mc.gameSettings.keyBindAttack.isKeyDown()) {
			return;
		}

		EntityPlayer target = findTarget(mc, me);
		if (target != null) {
			aimAt(me, target);
		}
	}

	private static EntityPlayer findTarget(Minecraft mc, EntityPlayerSP me) {
		EntityPlayer best = null;
		float bestAngle = Float.MAX_VALUE;
		for (EntityPlayer p : mc.theWorld.playerEntities) {
			if (p == me || p.isDead || p.getHealth() <= 0.0F || p.isSpectator() || me.isOnSameTeam(p)) {
				continue;
			}
			if (me.getDistanceToEntity(p) > range) {
				continue;
			}
			if (requireLineOfSight && !me.canEntityBeSeen(p)) {
				continue;
			}
			float[] r = rotationsTo(me, p);
			float dyaw = MathHelper.wrapAngleTo180_float(r[0] - me.rotationYaw);
			float dpitch = r[1] - me.rotationPitch;
			float angle = (float) Math.sqrt(dyaw * dyaw + dpitch * dpitch);
			if (angle <= fov && angle < bestAngle) {
				bestAngle = angle;
				best = p;
			}
		}
		return best;
	}

	private static float[] rotationsTo(EntityPlayerSP me, EntityPlayer t) {
		double px = t.posX + (t.posX - t.prevPosX) * predictTicks;
		double py = t.posY + t.height * aimHeight + (t.posY - t.prevPosY) * predictTicks;
		double pz = t.posZ + (t.posZ - t.prevPosZ) * predictTicks;

		double dx = px - me.posX;
		double dy = py - (me.posY + me.getEyeHeight());
		double dz = pz - me.posZ;
		double flat = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
		float pitch = (float) (-(Math.atan2(dy, flat) * 180.0D / Math.PI));
		return new float[] { yaw, pitch };
	}

	private static void aimAt(EntityPlayerSP me, EntityPlayer target) {
		float[] r = rotationsTo(me, target);
		float dyaw = MathHelper.wrapAngleTo180_float(r[0] - me.rotationYaw);
		float dpitch = r[1] - me.rotationPitch;

		me.rotationYaw += clamp(dyaw * smooth, -maxStep, maxStep);
		me.rotationPitch = clamp(me.rotationPitch + clamp(dpitch * smooth, -maxStep, maxStep), -90.0F, 90.0F);
	}

	private static float clamp(float v, float min, float max) {
		return v < min ? min : (v > max ? max : v);
	}
}
