package com.nhom15.drawguess.client.view;
import com.nhom15.drawguess.common.protocol.*;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
public class HistoryView extends VBox {
    private final ListView<HistoryData.Summary> matches=new ListView<>();
    private final Label details=new Label();
    private final Label status=new Label();
    private final ComboBox<HistoryData.Round> rounds = new ComboBox<>();
    private final ImageView drawing = new ImageView();
    private final Label imageStatus = new Label("Chọn một trận để xem tranh.");
    private final Label roundInfo = new Label();
    private final Button reloadImage = new Button("Tải lại ảnh");
    private final Consumer<Message> send;
    private String selectedId;
    public HistoryView(Consumer<Message> send,Runnable back) {
        this.send = send;
        setSpacing(12); setPadding(new Insets(24));
        Button refresh=new Button("Làm mới"); Button returnButton=new Button("Về danh sách phòng");
        refresh.setOnAction(e -> { clearSelection(); status.setText("Đang tải..."); send.accept(new Message(MessageType.GET_HISTORY)); });
        returnButton.setOnAction(e -> { clearSelection(); back.run(); });
        matches.setMinHeight(120); matches.setPrefHeight(140); details.setWrapText(true); details.setMaxWidth(Double.MAX_VALUE);
        details.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        details.setPadding(new Insets(14)); details.setStyle("-fx-font-size: 14px;");
        details.getStyleClass().add("history-details");
        ScrollPane detailScroll=new ScrollPane(details); detailScroll.setFitToWidth(true);
        rounds.setMaxWidth(Double.MAX_VALUE);
        rounds.setPromptText("Chọn vòng để xem tranh");
        rounds.getStyleClass().add("history-rounds");
        rounds.setConverter(new StringConverter<>() {
            @Override public String toString(HistoryData.Round r) {
                return r == null ? "" : "Vòng " + (r.index() + 1) + " — " + r.drawer();
            }
            @Override public HistoryData.Round fromString(String value) { return null; }
        });
        rounds.valueProperty().addListener((o, old, r) -> loadImage());
        drawing.setPreserveRatio(true);
        drawing.getStyleClass().add("history-image");
        drawing.setFitHeight(500);
        imageStatus.setWrapText(true);
        roundInfo.setWrapText(true);
        imageStatus.getStyleClass().add("history-image-status");
        reloadImage.setDisable(true);
        reloadImage.setOnAction(e -> loadImage());
        VBox preview = new VBox(12, new Label("Tranh của từng vòng"), rounds, roundInfo, imageStatus, reloadImage, drawing);
        preview.setPadding(new Insets(14));
        ScrollPane imageScroll = new ScrollPane(preview);
        imageScroll.setFitToWidth(true);
        drawing.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(1, Math.min(500, imageScroll.getViewportBounds().getWidth() - 30)),
                imageScroll.viewportBoundsProperty()));
        drawing.fitHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(180, Math.min(500, imageScroll.getViewportBounds().getHeight() - 200)),
                imageScroll.viewportBoundsProperty()));
        SplitPane content = new SplitPane(detailScroll, imageScroll);
        content.setDividerPositions(0.48);
        VBox.setVgrow(content, Priority.ALWAYS);
        matches.getSelectionModel().selectedItemProperty().addListener((o,old,m) -> {
            clearSelection(); selectedId=m==null ? null : m.id();
            if(m!=null) { status.setText("Đang tải chi tiết..."); send.accept(new Message(MessageType.GET_HISTORY_DETAIL,m.id())); }
        });
        getChildren().addAll(new Label("Lịch sử trận đấu — 200 trận gần nhất"),new HBox(12,refresh,returnButton),matches,status,content);
    }
    private void clearSelection() {
        selectedId = null;
        details.setText("");
        rounds.getItems().clear();
        rounds.setValue(null);
        drawing.setImage(null);
        roundInfo.setText("");
        reloadImage.setDisable(true);
        imageStatus.setText("Chọn một trận để xem tranh.");
    }
    private void loadImage() {
        drawing.setImage(null);
        HistoryData.Round r = rounds.getValue();
        reloadImage.setDisable(true);
        if (selectedId == null || r == null) { return; }
        roundInfo.setText("Người vẽ: " + r.drawer() + "\nTừ khóa: " + r.word());
        if (r.imagePath() == null || r.imagePath().isBlank()) {
            imageStatus.setText("Vòng này chưa có ảnh được lưu.");
            return;
        }
        imageStatus.setText("Đang tải tranh...");
        send.accept(new Message(MessageType.GET_HISTORY_IMAGE, new HistoryData.ImageRequest(selectedId, r.index())));
    }
    private boolean isSelected(String id, int round) {
        return selectedId != null && Objects.equals(selectedId, id)
                && rounds.getValue() != null && rounds.getValue().index() == round;
    }
    public void handleMessage(Message m) {
        switch(m.getType()) {
            case HISTORY_LIST -> {
                if(m.getData() instanceof List<?> rows && rows.stream().allMatch(HistoryData.Summary.class::isInstance)) {
                    clearSelection();
                    matches.getItems().setAll(rows.stream().map(HistoryData.Summary.class::cast).toList());
                    status.setText(rows.isEmpty() ? "Bạn chưa có trận đấu nào." : "Chọn một trận đã kết thúc để xem chi tiết.");
                }
            }
            case HISTORY_DETAIL -> {
                if(m.getData() instanceof HistoryData.Detail d && d.id().equals(selectedId)) {
                    StringBuilder text=new StringBuilder("Trận: "+d.id()+"\nPhòng: "+d.roomId()+" | Chủ đề: "+d.category()+" | "+HistoryData.statusLabel(d.status())+"\nBắt đầu: "+time(d.startedAt())+"\nKết thúc: "+time(d.endedAt())+"\n\nKẾT QUẢ\n");
                    for(ScoreEntry p:d.players()) text.append("Hạng ").append(p.getRank()).append(" — ").append(p.getUsername()).append(" — ").append(p.getScore()).append(" điểm | TB đoán: ").append(Double.isFinite(p.getAverageGuessTime()) ? String.format("%.2f giây",p.getAverageGuessTime()) : "Chưa đoán đúng").append("\n");
                    for(HistoryData.Round r:d.rounds()) {
                        text.append("\nVÒNG ").append(r.index()+1).append(" — Người vẽ: ").append(r.drawer()).append("\nTừ khóa: ").append(r.word()).append("\nẢnh trên server: ").append(r.imagePath()==null ? "Chưa gửi ảnh" : r.imagePath()).append("\nBắt đầu: ").append(time(r.startedAt())).append(" | Kết thúc: ").append(time(r.endedAt())).append("\n");
                        for(HistoryData.Guess g:r.guesses()) text.append(time(g.at())).append(" | ").append(g.username()).append(": ").append(g.answer()).append(g.correct() ? " — Đúng" : " — Sai").append(String.format(" (%.2f giây)",g.elapsedSeconds())).append("\n");
                    }
                    details.setText(text.toString()); status.setText("Đã tải kết quả.");
                    rounds.getItems().setAll(d.rounds());
                    rounds.getSelectionModel().selectFirst();
                }
            }
            case HISTORY_IMAGE -> {
                if (m.getData() instanceof HistoryData.DrawingImage data && isSelected(data.matchId(), data.roundIndex())) {
                    reloadImage.setDisable(false);
                    try {
                        byte[] png = data.png();
                        if (png == null || png.length == 0 || png.length >= 200 * 1024) { throw new IllegalArgumentException(); }
                        Image image = new Image(new ByteArrayInputStream(png));
                        if (image.isError() || image.getWidth() != 500 || image.getHeight() != 500) { throw new IllegalArgumentException(); }
                        drawing.setImage(image);
                        imageStatus.setText("Đã tải tranh của vòng " + (data.roundIndex() + 1) + ".");
                    } catch (RuntimeException e) { imageStatus.setText("Ảnh không hợp lệ. Hãy thử tải lại."); }
                }
            }
            case HISTORY_IMAGE_FAILED -> {
                if (m.getData() instanceof HistoryData.ImageFailure failure && isSelected(failure.matchId(), failure.roundIndex())) {
                    drawing.setImage(null);
                    reloadImage.setDisable(false);
                    imageStatus.setText(switch (failure.reason()) {
                        case "IMAGE_NOT_AVAILABLE" -> "Không tìm thấy ảnh hoặc file ảnh bị hỏng trên server.";
                        case "NOT_FOUND_OR_UNFINISHED" -> "Không có quyền xem ảnh hoặc trận chưa kết thúc.";
                        case "DATABASE_ERROR" -> "Không tải được ảnh do lỗi MySQL. Hãy thử lại.";
                        default -> "Không tải được ảnh. Hãy thử lại.";
                    });
                }
            }
            case HISTORY_FAILED -> status.setText("NOT_FOUND_OR_UNFINISHED".equals(m.getData()) ? "Trận chưa kết thúc hoặc không thuộc lịch sử của bạn." : "Không tải/lưu được lịch sử. Kiểm tra MySQL và chạy history.sql.");
            default -> { }
        }
    }
    private static String time(Long value) { return HistoryData.formatTime(value); }
}
