package com.fortnitelocker;

import com.fortnitelocker.LockerState.Rarity;
import com.fortnitelocker.LockerState.Skin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class EditSkinScreen extends Screen {
    private final LockerScreen parent;
    private final Skin skin;
    private Rarity rarity;
    private TextFieldWidget nameField;

    public EditSkinScreen(LockerScreen parent, Skin skin) {
        super(Text.literal("Edit Skin"));
        this.parent = parent;
        this.skin = skin;
        this.rarity = skin.rarity();
    }

    @Override
    protected void init() {
        int cx = width / 2, top = height / 2 - 80;

        nameField = new TextFieldWidget(textRenderer, cx - 100, top + 70, 200, 20, Text.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setText(skin.name());
        addDrawableChild(nameField);
        setInitialFocus(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Rarity: " + rarity.label()), b -> {
            rarity = rarity.next();
            b.setMessage(Text.literal("Rarity: " + rarity.label()));
        }).dimensions(cx - 100, top + 96, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> {
            String n = nameField.getText().trim();
            LockerState.setMeta(skin.id(), n.isEmpty() ? skin.name() : n, rarity);
            done();
        }).dimensions(cx - 100, top + 124, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> done())
            .dimensions(cx + 2, top + 124, 98, 20).build());

        if (!skin.bundled()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("§cDelete skin"), b -> {
                LockerState.delete(skin);
                done();
            }).dimensions(cx - 100, top + 148, 200, 20).build());
        }
    }

    private void done() {
        parent.refresh();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2, top = height / 2 - 80;
        LockerScreen.drawBackground(ctx, width, height);
        ctx.fill(cx - 112, top - 12, cx + 112, top + 180, 0xCC06163A);
        ctx.fill(cx - 112, top - 12, cx + 112, top - 10, 0xFF000000 | rarity.color);

        // live preview of the skin being edited
        int tileX = cx - 100, tileY = top - 2;
        int c = 0xFF000000 | rarity.color;
        ctx.fillGradient(tileX, tileY, tileX + 56, tileY + 62, c, 0xFF000000 | ((c >> 1) & 0x7F7F7F));
        LockerScreen.drawFigure(ctx, skin.texture(), tileX + 15, tileY + 4, 1.7f);

        ctx.drawText(textRenderer, Text.literal("§l§oEDIT SKIN"), cx - 36, top + 2, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, Text.literal(rarity.label()), cx - 36, top + 16, 0xFF000000 | rarity.color, true);
        ctx.drawText(textRenderer, Text.literal(skin.file() == null ? "" : skin.file().getFileName().toString()),
            cx - 36, top + 30, 0xFFAFC8F5, false);
        ctx.drawText(textRenderer, Text.literal("Name"), cx - 100, top + 60, 0xFFCCE0FF, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
