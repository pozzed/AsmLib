package me.hugenerd.load.config;

import me.hugenerd.load.transformer.MainTransformer;
import me.hugenerd.load.transformer.TestTransformer;
import net.futureclient.asm.config.Config;

public final class MemeConfig extends Config {

    public MemeConfig() {
        super("meme-mod");
        //this.addClassTransformers(new MainTransformer());
        this.addClassTransformers(TestTransformer.class);
    }
}
