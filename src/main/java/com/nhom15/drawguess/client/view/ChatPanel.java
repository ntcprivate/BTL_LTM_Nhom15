package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.ChatMessage;
import javafx.animation.PauseTransition;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

// Khung trò chuyện của phòng: danh sách tin nhắn và ô nhập. Tin của mình nằm bên phải, tô vàng;
// tin của người khác nằm bên trái kèm tên. Khi khung đang bị ẩn (cửa sổ hẹp) thì đếm số tin chưa đọc.
public class ChatPanel extends VBox {
    public static final int MAX_LENGTH = 200;
    private static final int MAX_MESSAGES = 200;
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter OTHER_DAY = DateTimeFormatter.ofPattern("HH:mm dd/MM");
    private final int userId;
    private final ListView<ChatMessage> list = new ListView<>();
    private final TextField input = new TextField();
    private final Button send = new Button();
    private final Label title = Icons.attach(new Label("Trò chuyện"), Icons.Glyph.MESSAGE, 18);
    private final Label notice = new Label();
    private final PauseTransition hideNotice = new PauseTransition(Duration.seconds(4));
    private final IntegerProperty unread = new SimpleIntegerProperty();
    private Consumer<String> onSend = text -> {};
    private boolean shown = true;

    public ChatPanel(int userId) {
        super(10);
        this.userId = userId;
        UiTheme.apply(this);
        getStyleClass().add("chat-panel");
        title.getStyleClass().add("section-title");

        list.getStyleClass().add("chat-list");
        list.setCellFactory(view -> new MessageCell());
        list.setFocusTraversable(false);
        Label empty = new Label("Chưa có tin nhắn. Gửi lời chào cả phòng nhé!");
        empty.getStyleClass().add("subtle-text");
        empty.setWrapText(true);
        list.setPlaceholder(empty);
        VBox.setVgrow(list, Priority.ALWAYS);

        notice.getStyleClass().add("chat-notice");
        notice.setWrapText(true);
        notice.setMinHeight(Region.USE_PREF_SIZE);
        notice.setVisible(false);
        notice.setManaged(false);
        hideNotice.setOnFinished(event -> {
            notice.setVisible(false);
            notice.setManaged(false);
        });

        input.setPromptText("Nhắn cho cả phòng...");
        input.setAccessibleText("Tin nhắn trò chuyện");
        input.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= MAX_LENGTH ? change : null));
        input.setOnAction(event -> submit());
        Icons.attach(send, Icons.Glyph.SEND, 16);
        send.getStyleClass().addAll("secondary-button", "chat-send");
        send.setAccessibleText("Gửi tin nhắn");
        send.setTooltip(new Tooltip("Gửi tin nhắn (Enter)"));
        send.setOnAction(event -> submit());
        HBox inputRow = new HBox(8, input, send);
        inputRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(input, Priority.ALWAYS);

        getChildren().addAll(title, list, notice, inputRow);
    }

    public void setOnSend(Consumer<String> action) {
        onSend = action;
    }

    private void submit() {
        String text = input.getText().strip();
        if (text.isEmpty()) { return; }
        onSend.accept(text);
        input.clear();
    }

    public void add(ChatMessage message) {
        list.getItems().add(message);
        if (list.getItems().size() > MAX_MESSAGES) { list.getItems().removeFirst(); }
        list.scrollTo(list.getItems().size() - 1);
        if (!shown) { unread.set(unread.get() + 1); }
    }

    // Tin nhắn đã có từ trước (ví dụ chat ở phòng chờ trước khi vào trận); không tính là chưa đọc.
    public void addHistory(java.util.List<ChatMessage> messages) {
        list.getItems().addAll(messages);
        while (list.getItems().size() > MAX_MESSAGES) { list.getItems().removeFirst(); }
        if (!list.getItems().isEmpty()) { list.scrollTo(list.getItems().size() - 1); }
    }

    public void clear() {
        list.getItems().clear();
        unread.set(0);
    }

    // Lỗi gửi tin (lộ từ khóa, gửi quá nhanh...) hiện ngay trên ô nhập rồi tự ẩn.
    public void showNotice(String text) {
        notice.setText(text);
        notice.setVisible(true);
        notice.setManaged(true);
        hideNotice.playFromStart();
    }

    public void setTitleVisible(boolean visible) {
        title.setVisible(visible);
        title.setManaged(visible);
    }

    // Khung đang hiện trên màn hình thì không có tin chưa đọc.
    public void setShown(boolean value) {
        shown = value;
        if (value) { unread.set(0); }
    }

    public ReadOnlyIntegerProperty unreadProperty() {
        return unread;
    }

    public TextField getInput() {
        return input;
    }

    // Giờ gửi tin theo giờ máy người xem: "14:05" nếu là hôm nay, kèm ngày nếu là hôm khác.
    public static String formatTime(long sentAt) {
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(sentAt), ZoneId.systemDefault());
        return time.format(time.toLocalDate().equals(LocalDate.now()) ? TODAY : OTHER_DAY);
    }

    private class MessageCell extends ListCell<ChatMessage> {
        private final Label name = new Label();
        private final Label time = new Label();
        private final Label text = new Label();
        private final HBox header = new HBox(8, name, time);
        private final VBox bubble = new VBox(2, header, text);
        private final HBox row = new HBox(bubble);

        MessageCell() {
            name.getStyleClass().add("chat-name");
            time.getStyleClass().add("chat-time");
            time.setMinWidth(Region.USE_PREF_SIZE);
            header.setAlignment(Pos.BASELINE_LEFT);
            text.getStyleClass().add("chat-text");
            text.setWrapText(true);
            text.setMinHeight(Region.USE_PREF_SIZE);
            bubble.getStyleClass().add("chat-bubble");
            bubble.maxWidthProperty().bind(list.widthProperty().subtract(56));
            // Ô không tự đòi rộng hơn danh sách, tránh hiện thanh cuộn ngang.
            setPrefWidth(0);
        }

        @Override protected void updateItem(ChatMessage message, boolean empty) {
            super.updateItem(message, empty);
            setText(null);
            if (empty || message == null) {
                setGraphic(null);
                return;
            }
            boolean mine = message.getUserId() == userId;
            name.setText(mine ? "Bạn" : message.getUsername());
            String sent = formatTime(message.getSentAt());
            time.setText(sent);
            text.setText(message.getText());
            bubble.getStyleClass().removeAll("chat-mine", "chat-theirs");
            bubble.getStyleClass().add(mine ? "chat-mine" : "chat-theirs");
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            setAccessibleText((mine ? "Bạn" : message.getUsername()) + ", lúc " + sent + ": " + message.getText());
            setGraphic(row);
        }
    }
}
