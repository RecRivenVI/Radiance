package com.radiance.client.gui;

import com.radiance.api.render.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Standard paginated screen; census exists only while this screen is open. */
public final class RenderStatusScreen extends Screen {
    private final Screen parent;
    private int page;

    public RenderStatusScreen(Screen parent) {
        super(Component.translatable("radiance.routes.title"));
        this.parent = parent;
        RenderRoutes.start();
    }

    protected void init() {
        addRenderableWidget(
                Button.builder(Component.literal("←"), b -> page = Math.max(0, page - 1))
                        .bounds(width / 2 - 155, height - 30, 45, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("→"), b -> page++)
                        .bounds(width / 2 - 105, height - 30, 45, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), b -> onClose())
                        .bounds(width / 2 + 10, height - 30, 145, 20)
                        .build());
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void onClose() {
        minecraft.setScreen(parent);
    }

    public void removed() {
        RenderRoutes.stop();
        super.removed();
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("API " + RenderPlatform.API_VERSION + " · 仅统计当前窗口的 CPU 请求和消费，不代表 GPU 完成或目视通过");
        lines.add("能力：区块／实体／方块实体／物品／粒子／GUI／屏幕效果；无运行记录表示尚未遇到");
        lines.add("执行能力：区段按变化发布；实体／BER／物品使用局部资源或捕获；地图等保留原回调");
        lines.add("普通粒子参数逐帧；动态捕获逐帧；GUI 光栅与效果即时执行，实际使用见下方记录");
        var counters = PersistentInstances.counters();
        lines.add("持久实例 " + counters[0] + "；累计变化 " + counters[1] + "；视图选择 " + counters[2]);
        var availability = RenderPlatform.availability();
        if (availability.isEmpty()) lines.add("Radiance Extra：未注册；第三方仅使用本体的标准入口与有限捕获");
        availability.forEach((id, reason) -> lines.add(id + "：" + reason));
        for (var row : RenderRoutes.snapshot()) {
            lines.add(row.key().family() + " / " + row.key().source() + " / " + row.key().domain());
            lines.add(
                    "  "
                            + row.key().route()
                            + " 请求="
                            + row.requested()
                            + " CPU消费="
                            + row.submitted()
                            + " 未消费/拒绝="
                            + row.rejected()
                            + " → "
                            + row.executor());
            if (!row.reason().isEmpty()) lines.add("  " + row.reason());
        }
        if (RenderRoutes.snapshot().isEmpty()) lines.add("尚未记录绘制；请在已进入世界时打开此页，世界会继续渲染。");
        return List.copyOf(lines);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffffffff);
        List<String> lines = lines();
        int count = Math.max(1, (height - 70) / 12);
        page = Math.min(page, Math.max(0, (lines.size() - 1) / count));
        for (int i = page * count, y = 35;
                i < Math.min(lines.size(), (page + 1) * count);
                i++, y += 12)
            graphics.drawString(
                    font, font.plainSubstrByWidth(lines.get(i), width - 24), 12, y, 0xffeeeeee);
        graphics.drawCenteredString(
                font,
                (page + 1) + " / " + Math.max(1, (lines.size() + count - 1) / count),
                width / 2,
                height - 24,
                0xffaaaaaa);
    }
}
