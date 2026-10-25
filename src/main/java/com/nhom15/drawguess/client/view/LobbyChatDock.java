package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.ChatMessage;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

// Chat của phòng chờ. Thu gọn: một dòng với biểu tượng chat và tin nhắn mới nhất.
// Bấm biểu tượng (hoặc dòng tin) thì mở toàn bộ đoạn chat; bấm "Thu gọn" để trở lại.
public class LobbyChatDock extends VBox {
    private final int userId;
    private final ChatPanel chat;
    private final HBox bar;
    private final VBox panel;
    private final Button open = new Button();
    private final Label badge = new Label();
    private final Label previewName = new Label();
    private final Label previewText = new Label("Chưa có tin nhắn. Bấm biểu tượng để trò chuyện với cả phòng.");
    private final BooleanProperty expanded = new SimpleBooleanProperty();

    public LobbyChatDock(int userId) {
        this.userId = userId;
        chat = new ChatPanel(userId);
        chat.setTitleVisible(false);
        VBox.setVgrow(chat, Priority.ALWAYS);

        Icons.attach(open, Icons.Glyph.MESSAGE, 20);
        open.getStyleClass().add("chat-dock-toggle");
        open.setTooltip(new Tooltip("Mở toàn bộ đoạn chat"));
        open.setOnAction(event -> setExpanded(true));
        badge.getStyleClass().add("chat-dock-badge");
        badge.setMouseTransparent(true);
        StackPane icon = new StackPane(open, badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        icon.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        previewName.getStyleClass().add("chat-dock-name");
        previewName.setVisible(false);
        previewName.setManaged(false);
        previewText.getStyleClass().addAll("chat-dock-text", "chat-dock-empty");
        previewText.setMaxWidth(Double.MAX_VALUE);
        VBox preview = new VBox(1, previewName, previewText);
        preview.setMinWidth(0);
        preview.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(preview, Priority.ALWAYS);
        bar = new HBox(12, icon, preview);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("chat-dock");
        bar.setCursor(Cursor.HAND);
        bar.setOnMouseClicked(event -> setExpanded(true));

        Label title = Icons.attach(new Label("Trò chuyện"), Icons.Glyph.MESSAGE, 18);
        title.getStyleClass().add("section-title");
        Button collapse = Icons.attach(new Button("Thu gọn"), Icons.Glyph.CHEVRON_DOWN, 15);
        collapse.getStyleClass().addAll("ghost-button", "chat-dock-collapse");
        collapse.setMinWidth(Region.USE_PREF_SIZE);
        collapse.setTooltip(new Tooltip("Chỉ hiện tin nhắn mới nhất"));
        collapse.setOnAction(event -> setExpanded(false));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(8, title, spacer, collapse);
        heading.setAlignment(Pos.CENTER_LEFT);
        panel = new VBox(10, heading, chat);
        panel.getStyleClass().add("chat-dock-panel");
        VBox.setVgrow(panel, Priority.ALWAYS);

        chat.unreadProperty().addListener((observable, before, count) -> renderBadge());
        expanded.addListener((observable, before, value) -> render());
        render();
    }

    public void setExpanded(boolean value) {
        expanded.set(value);
    }

    public boolean isExpanded() {
        return expanded.get();
    }

    public BooleanProperty expandedProperty() {
        return expanded;
    }

    private void render() {
        boolean value = expanded.get();
        getChildren().setAll(value ? panel : bar);
        chat.setShown(value);
        renderBadge();
        if (value) { chat.getInput().requestFocus(); }
    }

    private void renderBadge() {
        int count = chat.unreadProperty().get();
        badge.setText(count > 9 ? "9+" : String.valueOf(count));
        badge.setVisible(count > 0);
        open.setAccessibleText(count > 0 ? "Mở khung trò chuyện, " + count + " tin chưa đọc" : "Mở khung trò chuyện");
    }

    public void setOnSend(Consumer<String> action) {
        chat.setOnSend(action);
    }

    public void add(ChatMessage message) {
        chat.add(message);
        boolean mine = message.getUserId() == userId;
        previewName.setText(mine ? "Bạn" : message.getUsername());
        previewName.setVisible(true);
        previewName.setManaged(true);
        previewText.setText(message.getText());
        previewText.getStyleClass().remove("chat-dock-empty");
    }

    public void showNotice(String text) {
        setExpanded(true);
        chat.showNotice(text);
    }

    // Vào phòng khác thì bắt đầu đoạn chat mới.
    public void clear() {
        chat.clear();
        previewName.setVisible(false);
        previewName.setManaged(false);
        previewText.setText("Chưa có tin nhắn. Bấm biểu tượng để trò chuyện với cả phòng.");
        if (!previewText.getStyleClass().contains("chat-dock-empty")) { previewText.getStyleClass().add("chat-dock-empty"); }
        setExpanded(false);
    }

    public ChatPanel getChat() {
        return chat;
    }
}
