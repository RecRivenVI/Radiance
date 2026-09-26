package com.radiance.audit.benchmark.adapter;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value="radiance_audit",dist=Dist.CLIENT)
public final class NeoForgeBenchmarkMod {
    public NeoForgeBenchmarkMod(){PortableBenchmarkMod.initialize();}
}
