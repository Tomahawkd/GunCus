package de.cas_ual_ty.guncus.entity;

import de.cas_ual_ty.guncus.GunCus;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ThrowableEntity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EntityBullet extends ThrowableEntity
{
    protected static final DataParameter<Float> DAMAGE = EntityDataManager.createKey(EntityBullet.class, DataSerializers.FLOAT);
    protected static final DataParameter<Float> GRAVITY = EntityDataManager.createKey(EntityBullet.class, DataSerializers.FLOAT);
    
    public EntityBullet(EntityType<EntityBullet> type, World worldIn)
    {
        super(type, worldIn);
    }
    
    public EntityBullet(EntityType<EntityBullet> type, LivingEntity livingEntityIn, World worldIn)
    {
        super(type, livingEntityIn, worldIn);
    }
    
    public EntityBullet setDamage(float damage)
    {
        this.dataManager.set(EntityBullet.DAMAGE, damage);
        return this;
    }
    
    public EntityBullet setGravity(float gravity)
    {
        this.dataManager.set(EntityBullet.GRAVITY, gravity);
        return this;
    }
    
    @Override
    public void tick()
    {
        super.tick();
        
        this.spawnParticles();
        
        if (this.ticksExisted >= 20)
        {
            this.remove();
        }
    }
    
    public void spawnParticles()
    {
        Vec3d vec3d = this.getMotion();
        double d1 = vec3d.x;
        double d2 = vec3d.y;
        double d0 = vec3d.z;
        for (int i = 0; i < 4; ++i)
        {
            this.world.addParticle(ParticleTypes.CRIT, this.posX + d1 * (double) i / 4.0D, this.posY + d2 * (double) i / 4.0D, this.posZ + d0 * (double) i / 4.0D, -d1, -d2 + 0.2D, -d0);
            this.world.addParticle(ParticleTypes.CLOUD, this.posX + d1 * (double) i / 4.0D, this.posY + d2 * (double) i / 4.0D, this.posZ + d0 * (double) i / 4.0D, -d1, -d2 + 0.2D, -d0);
            this.world.addParticle(ParticleTypes.EXPLOSION, this.posX + d1 * (double) i / 4.0D, this.posY + d2 * (double) i / 4.0D, this.posZ + d0 * (double) i / 4.0D, -d1, -d2 + 0.2D, -d0);
            this.world.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.posX + d1 * (double) i / 4.0D, this.posY + d2 * (double) i / 4.0D, this.posZ + d0 * (double) i / 4.0D, -d1, -d2 + 0.2D, -d0);
        }
    }
    
    @Override
    protected void onImpact(RayTraceResult result)
    {
        if (result.getType() == Type.ENTITY)
        {
            EntityRayTraceResult hit = (EntityRayTraceResult) result;
            
            if (hit.getEntity() == this.getThrower() && this.ticksExisted <= 5)
            {
                return;
            }
            
            if (!this.world.isRemote && hit.getEntity() instanceof LivingEntity)
            {
                LivingEntity entity = (LivingEntity) hit.getEntity();
                entity.attackEntityFrom(DamageSource.causeMobDamage(this.getThrower()), this.getBulletDamage());
                
                if (this.getThrower() instanceof PlayerEntity)
                {
                    GunCus.proxy.addHitmarker((PlayerEntity) this.getThrower());
                }
            }
        }
        
        this.remove();
    }
    
    @Override
    protected void registerData()
    {
        this.dataManager.register(EntityBullet.DAMAGE, 4F);
        this.dataManager.register(EntityBullet.GRAVITY, 1F);
    }
    
    @Override
    protected float getGravityVelocity()
    {
        return super.getGravityVelocity() * this.getBulletGravity();
    }
    
    protected float getBulletDamage()
    {
        return this.dataManager.get(EntityBullet.DAMAGE);
    }
    
    protected float getBulletGravity()
    {
        return this.dataManager.get(EntityBullet.GRAVITY);
    }
}
