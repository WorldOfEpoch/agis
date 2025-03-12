package atavism.agis.plugins;

import atavism.agis.objects.Bag;

public class ItemLocation {

    final Bag bag;
    final int slot;

    public ItemLocation(Bag bag, int slot) {
        this.bag = bag;
        this.slot = slot;
    }

}
