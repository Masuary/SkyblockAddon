package yorickbm.guilibrary.interfaces;

import net.minecraft.world.MenuProvider;

public abstract class MenuProviderInterface implements MenuProvider {
    protected ServerInterface container;
    public ServerInterface getContainer() {  return this.container; }
}
