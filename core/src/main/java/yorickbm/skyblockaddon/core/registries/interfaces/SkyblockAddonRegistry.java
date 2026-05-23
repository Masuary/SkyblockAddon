package yorickbm.skyblockaddon.core.registries.interfaces;

public abstract class SkyblockAddonRegistry<T> {
    protected int index = 0;

    /**
     * Get registries next data item and increment index by one
     * @param component Return object of component
     */
    public abstract void getNextData(T component);

    /**
     * Get size of registry
     * @return Amount of items in registry
     */
    public abstract int getSize();

    /**
     * Set the index where the Registry should start from
     * @param number Start index
     */
    public void setIndex(final int number) {
        if(number < 0) {
            this.index = 0;
        }
        else if (number > this.getSize()) {
            this.index = this.getSize()-1;
        }
        else {
            this.index = number;
        }
    }

    /**
     * Get registries current index
     * @return Integer index
     */
    public int getIndex() { return this.index; }

    /**
     * Determine if registry has next data item
     * @return True if registry has another data item
     */
    public boolean hasNext() {
        return this.getIndex() < this.getSize();
    }
}
