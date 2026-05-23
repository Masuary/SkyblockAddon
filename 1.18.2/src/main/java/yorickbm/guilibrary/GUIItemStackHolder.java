package yorickbm.guilibrary;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import yorickbm.guilibrary.util.Helper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GUIItemStackHolder implements Cloneable {

    private Item item;
    private int amount;
    private CompoundTag tag;

    private List<TextComponent> display_name;
    private List<List<TextComponent>> lore;

    public GUIItemStackHolder(final Item item, final int amount, final List<TextComponent> display_name, final List<List<TextComponent>> lore, final CompoundTag tag) {
        this.display_name = display_name;
        this.lore = lore;
        this.tag = tag;
        this.item = item;
        this.amount = amount;
    }
    public GUIItemStackHolder(final Item item, final int amount, final CompoundTag tag, final List<String> display_name, final List<List<String>> lore) {
        this(item, amount, convertToTextComponents(display_name), convertToTextComponentLists(lore), tag);
    }

    private static List<TextComponent> convertToTextComponents(final List<String> input) {
        return (input != null) ? input.stream()
                .map(Component.Serializer::fromJson)
                .filter(c -> c instanceof TextComponent)
                .map(c -> (TextComponent) c)
                .collect(Collectors.toList()) : new ArrayList<>();
    }

    private static List<List<TextComponent>> convertToTextComponentLists(final List<List<String>> input) {
        return (input != null) ? input.stream()
                .map(GUIItemStackHolder::convertToTextComponents)
                .collect(Collectors.toList()) : new ArrayList<>();
    }

    public List<List<TextComponent>> getLore() { return this.lore; }
    public void setLore(final List<List<TextComponent>> lore) { this.lore = lore; }

    public List<TextComponent> getDisplayName() { return this.display_name; }
    public void setDisplayName(final List<TextComponent> display_name) { this.display_name = display_name; }

    public void setItem(final Item item) { this.item = item; }
    public Item getItem() { return this.item; }

    public void setAmount(final int amount) { this.amount = amount; }
    public int getAmount() { return this.amount; }

    public ItemStack getItemStack() {
        final ItemStack stack = new ItemStack(this.item, this.amount);
        stack.getOrCreateTag().put(GUILibraryRegistry.MOD_ID, this.tag);

        if(this.tag.contains("SkullOwner")) {
            stack.getOrCreateTag().putString("SkullOwner", this.tag.getString("SkullOwner"));
        }
        if(this.tag.contains("SkullTexture")) {
            stack.getOrCreateTag().putString("SkullTexture", this.tag.getString("SkullTexture"));
        }

        stack.setHoverName(this.display_name.stream().reduce(new TextComponent(""), (a, b) -> (TextComponent) a.append(b)));

        Helper.addLore(stack, this.lore.stream()
                .map(innerList -> innerList.stream()
                        .reduce(new TextComponent(""), (a, b) -> (TextComponent) a.append(b))
                ).toArray(TextComponent[]::new));

        return stack;
    }

    public void putData(final String key, final String value) {
        this.tag.putString(key, value);
    }
    public void setData(final CompoundTag tag) {
        this.tag = tag;
    }
    public CompoundTag getData() {
        return this.tag;
    }

    @Override
    public GUIItemStackHolder clone() {
        try {
            // Create a deep copy of the CompoundTag
            final CompoundTag clonedTag = this.tag.copy();

            // Deep copy the display name (including style)
            final List<TextComponent> clonedDisplayName = this.display_name.stream()
                    .map(component -> (TextComponent) new TextComponent(component.getText()).setStyle(component.getStyle()))
                    .toList();

            // Deep copy the lore (including style)
            final List<List<TextComponent>> clonedLore = this.lore.stream()
                    .map(innerList -> innerList.stream()
                            .map(component -> (TextComponent) new TextComponent(component.getText()).setStyle(component.getStyle()))
                            .collect(Collectors.toList()))
                    .toList();

            return new GUIItemStackHolder(this.item, this.amount,
                    clonedDisplayName,
                    clonedLore,
                    clonedTag);
        } catch (final Exception e) {
            throw new RuntimeException("Cloning failed", e);
        }
    }
}
