package com.fletime.riatoriifind.gui;

import com.fletime.riatoriifind.service.ToriiDataService.FindEntry;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.List;

public class FindResultsScreen extends Screen {

    private static final int PER_PAGE = 10;
    private static final int ROW_H = 14;
    private static final int PANEL_W = 340;
    private static final int PANEL_TOP = 40;

    private final List<FindEntry> results;
    private final String query;
    private final int totalPages;
    private int page;

    public FindResultsScreen(List<FindEntry> results, String query) {
        super(Component.translatable("riatoriifind.gui.title"));
        this.results = results;
        this.query = query;
        this.totalPages = Math.max(1, (int) Math.ceil((double) results.size() / PER_PAGE));
        this.page = 0;
    }

    @Override
    protected void init() {
        rebuildPage();
    }

    private int panelLeft() {
        return (width - PANEL_W) / 2;
    }

    private void rebuildPage() {
        clearWidgets();
        int pl = panelLeft();
        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, results.size());

        // WIKI buttons for each entry
        for (int i = start; i < end; i++) {
            var entry = results.get(i);
            int row = i - start;
            int ey = PANEL_TOP + 18 + row * ROW_H;

            addRenderableWidget(Button.builder(
                    Component.literal("[WIKI]"),
                    b -> openWiki(entry.name())
            ).bounds(pl + PANEL_W - 55, ey + 1, 45, ROW_H - 2).build());
        }

        // Prev / Close / Next buttons
        int cx = width / 2;
        addRenderableWidget(Button.builder(
                Component.literal("< ").append(Component.translatable("riatoriifind.gui.prev")),
                b -> changePage(-1)
        ).bounds(cx - 120, height - 40, 60, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("riatoriifind.gui.close"),
                b -> onClose()
        ).bounds(cx - 25, height - 40, 50, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("riatoriifind.gui.next").append(" >"),
                b -> changePage(1)
        ).bounds(cx + 60, height - 40, 60, 20).build());
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(totalPages - 1, page + delta));
        rebuildPage();
    }

    private void openWiki(String name) {
        try {
            Util.getPlatform().openUri(URI.create("https://wiki.ria.red/wiki/" + name));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        int pl = panelLeft();
        int pr = pl + PANEL_W;
        int panelBottom = PANEL_TOP + Math.min(PER_PAGE, results.size()) * ROW_H + 40;

        // Panel background
        g.fill(pl, PANEL_TOP - 10, pr, panelBottom, 0xC0101010);

        // Title
        var titleText = Component.translatable("riatoriifind.gui.title_text", query, results.size());
        g.text(font, titleText, width / 2 - font.width(titleText) / 2, PANEL_TOP - 6, 0xFFFFFFFF, false);

        // Header
        int headY = PANEL_TOP + 4;
        g.text(font, Component.translatable("riatoriifind.result.col.id"), pl + 8, headY, 0xFF888888, false);
        g.text(font, Component.translatable("riatoriifind.result.col.grade"), pl + 50, headY, 0xFF888888, false);
        g.text(font, Component.translatable("riatoriifind.result.col.name"), pl + 100, headY, 0xFF888888, false);
        g.fill(pl + 8, headY + 10, pr - 8, headY + 11, 0xFF888888);

        // Entries
        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, results.size());

        for (int i = start; i < end; i++) {
            var entry = results.get(i);
            int row = i - start;
            int ey = headY + 14 + row * ROW_H;

            if ((row % 2) == 0) g.fill(pl + 4, ey, pr - 4, ey + ROW_H, 0x15FFFFFF);

            g.text(font, entry.id(), pl + 8, ey + 2, 0xFFFFFFFF, false);
            g.text(font, entry.grade(), pl + 50, ey + 2, 0xFFFFFFFF, false);
            g.text(font, entry.name(), pl + 100, ey + 2, 0xFFFFFFFF, false);
        }

        // Page info
        if (totalPages > 1) {
            var pageText = Component.translatable("riatoriifind.gui.page", page + 1, totalPages);
            g.text(font, pageText, width / 2 - font.width(pageText) / 2, height - 60, 0xFF888888, false);
        }

        if (results.isEmpty()) {
            var empty = Component.translatable("riatoriifind.result.empty.find");
            g.text(font, empty, width / 2 - font.width(empty) / 2, height / 2, 0xFF888888, false);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
