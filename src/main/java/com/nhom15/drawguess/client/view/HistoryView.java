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
    private final Label overviewTitle = new Label("Chọn một trận để xem kết quả");
    private final Label overviewMeta = new Label();
    private final VBox rankingCards = new VBox(8);
    private final VBox roundCards = new VBox(8);
    private final VBox guessCards = new VBox(7);
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
        UiTheme.apply(this);
        getStyleClass().add("content-card");
        setSpacing(14); setPadding(new Insets(22));
        setMaxWidth(Double.MAX_VALUE); setMaxHeight(Double.MAX_VALUE);
        Button refresh=new Button("Làm mới"); Button returnButton=new Button("Về trang chủ");
        refresh.getStyleClass().add("secondary-button");
        returnButton.getStyleClass().add("ghost-button");
        refresh.setOnAction(e -> { clearSelection(); status.setText("Đang tải..."); UiTheme.status(status,"info"); send.accept(new Message(MessageType.GET_HISTORY)); });
        returnButton.setOnAction(e -> { clearSelection(); back.run(); });
        matches.setMinHeight(110); matches.setPrefHeight(220);
        matches.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(HistoryData.Summary match, boolean empty) {
                super.updateItem(match, empty);
                if (empty || match == null) { setText(null); setGraphic(null); return; }
                Label category = new Label(match.category()); category.getStyleClass().add("history-match-category");
                Label time = new Label(HistoryData.formatTime(match.startedAt())); time.getStyleClass().add("history-match-time");
                VBox identity = new VBox(3, category, time);
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                Label state = new Label(HistoryData.statusLabel(match.status()));
                state.getStyleClass().addAll("history-state", "history-state-" + match.status().toLowerCase());
                Label score = new Label(match.score() + " điểm"); score.getStyleClass().add("history-score");
                Label rank = new Label("Hạng " + match.rank()); rank.getStyleClass().add("history-rank");
                HBox row = new HBox(12, identity, spacer, state, score, rank);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.getStyleClass().add("history-match-row");
                setText(null); setGraphic(row);
            }
        });
        details.setWrapText(true); details.setMaxWidth(Double.MAX_VALUE);
        details.setManaged(false); details.setVisible(false);
        details.getStyleClass().add("history-details");
        overviewTitle.getStyleClass().add("section-title");
        overviewMeta.getStyleClass().add("subtle-text");
        overviewMeta.setWrapText(true);
        Label rankingTitle = new Label("Bảng xếp hạng"); rankingTitle.getStyleClass().add("history-block-title");
        Label roundsTitle = new Label("Các vòng đã chơi"); roundsTitle.getStyleClass().add("history-block-title");
        VBox overview = new VBox(13, overviewTitle, overviewMeta, rankingTitle, rankingCards,
                new Separator(), roundsTitle, roundCards, details);
        overview.getStyleClass().add("history-overview");
        ScrollPane detailScroll=new ScrollPane(overview); detailScroll.setFitToWidth(true);
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
        Label previewTitle = new Label("Tranh của từng vòng");
        previewTitle.getStyleClass().add("section-title");
        Label guessesTitle = new Label("Lượt đoán");
        guessesTitle.getStyleClass().add("history-block-title");
        reloadImage.getStyleClass().add("secondary-button");
        roundInfo.getStyleClass().add("round-summary");
        imageStatus.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(imageStatus, Priority.ALWAYS);
        reloadImage.setMinWidth(Region.USE_PREF_SIZE);
        HBox imageActions = new HBox(10, imageStatus, reloadImage);
        imageActions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox preview = new VBox(12, previewTitle, rounds, roundInfo, imageActions,
                drawing, guessesTitle, guessCards);
        preview.setPadding(new Insets(14));
        ScrollPane imageScroll = new ScrollPane(preview);
        imageScroll.setFitToWidth(true);
        drawing.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(1, Math.min(500, imageScroll.getViewportBounds().getWidth() - 30)),
                imageScroll.viewportBoundsProperty()));
        // Tranh vuông nên chiều cao theo chiều rộng khung, để ảnh to nhất có thể.
        drawing.fitHeightProperty().bind(drawing.fitWidthProperty());
        SplitPane content = new SplitPane(detailScroll, imageScroll);
        content.setDividerPositions(0.46);
        // Danh sách trận và phần chi tiết chia theo chiều dọc; kéo vạch ngăn để đổi kích thước.
        SplitPane body = new SplitPane(matches, content);
        body.setOrientation(javafx.geometry.Orientation.VERTICAL);
        body.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(matches, false);
        VBox.setVgrow(body, Priority.ALWAYS);
        matches.getSelectionModel().selectedItemProperty().addListener((o,old,m) -> {
            clearSelection(); selectedId=m==null ? null : m.id();
            if(m!=null) { status.setText("Đang tải chi tiết..."); UiTheme.status(status,"info"); send.accept(new Message(MessageType.GET_HISTORY_DETAIL,m.id())); }
        });
        Label title = new Label("Lịch sử trận đấu");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Xem lại kết quả và tranh của 200 trận gần nhất");
        subtitle.getStyleClass().add("subtle-text");
        VBox heading = new VBox(3, title, subtitle);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        ArtworkCard painting = new ArtworkCard(
                "/images/Landscape-from-Saint-Remy-1889.jpg", "Landscape from Saint-Rémy • 1889", 300, 82);
        HBox header = new HBox(16, heading, headerSpacer, painting);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        UiTheme.status(status, "info");
        getChildren().addAll(header, new HBox(12,refresh,returnButton),status,body);
    }
    private void clearSelection() {
        selectedId = null;
        details.setText("");
        overviewTitle.setText("Chọn một trận để xem kết quả");
        overviewMeta.setText("");
        rankingCards.getChildren().clear();
        roundCards.getChildren().clear();
        guessCards.getChildren().clear();
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
        renderRound(r);
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

    private void renderOverview(HistoryData.Detail detail) {
        overviewTitle.setText(String.format("Phòng %03d  •  %s", detail.roomId(), detail.category()));
        overviewMeta.setText(HistoryData.statusLabel(detail.status()) + "   •   "
                + HistoryData.formatTime(detail.startedAt()) + " → " + HistoryData.formatTime(detail.endedAt()));
        rankingCards.getChildren().setAll(detail.players().stream().map(player -> {
            Label rank = new Label(String.valueOf(player.getRank())); rank.getStyleClass().add("ranking-position");
            Label name = new Label(player.getUsername()); name.getStyleClass().add("ranking-name");
            String average = Double.isFinite(player.getAverageGuessTime())
                    ? String.format("Trung bình %.2f giây", player.getAverageGuessTime()) : "Chưa đoán đúng";
            Label averageTime = new Label(average); averageTime.getStyleClass().add("ranking-meta");
            VBox identity = new VBox(2, name, averageTime);
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            Label score = new Label(player.getScore() + " điểm"); score.getStyleClass().add("ranking-score");
            HBox card = new HBox(11, rank, identity, spacer, score);
            card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            card.getStyleClass().add("ranking-card");
            return card;
        }).toList());
        roundCards.getChildren().setAll(detail.rounds().stream().map(round -> {
            Label number = new Label("V" + (round.index() + 1)); number.getStyleClass().add("round-number");
            Label drawer = new Label(round.drawer()); drawer.getStyleClass().add("round-drawer");
            Label word = new Label("Từ khóa: " + round.word()); word.getStyleClass().add("round-word");
            VBox text = new VBox(2, drawer, word);
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            Label guesses = new Label(round.guesses().size() + " lượt đoán"); guesses.getStyleClass().add("round-count");
            HBox card = new HBox(11, number, text, spacer, guesses);
            card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            card.getStyleClass().add("round-card");
            return card;
        }).toList());
    }

    private void renderRound(HistoryData.Round round) {
        roundInfo.setText("Vòng " + (round.index() + 1) + "   •   Người vẽ: " + round.drawer()
                + "   •   Từ khóa: " + round.word());
        guessCards.getChildren().setAll(round.guesses().stream().map(guess -> {
            Label player = new Label(guess.username()); player.getStyleClass().add("guess-player");
            Label answer = new Label(guess.answer()); answer.getStyleClass().add("guess-answer");
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            Label result = new Label(guess.correct() ? "✓ Đúng" : "Sai");
            result.getStyleClass().addAll("guess-result", guess.correct() ? "guess-correct" : "guess-wrong");
            Label elapsed = new Label(String.format("%.2f giây", guess.elapsedSeconds()));
            elapsed.getStyleClass().add("guess-time");
            HBox card = new HBox(8, player, answer, spacer, elapsed, result);
            card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            card.getStyleClass().add("guess-card");
            return card;
        }).toList());
        if (round.guesses().isEmpty()) {
            Label empty = new Label("Chưa có lượt đoán trong vòng này.");
            empty.getStyleClass().add("subtle-text");
            guessCards.getChildren().setAll(empty);
        }
    }
    public void handleMessage(Message m) {
        switch(m.getType()) {
            case HISTORY_LIST -> {
                if(m.getData() instanceof List<?> rows && rows.stream().allMatch(HistoryData.Summary.class::isInstance)) {
                    clearSelection();
                    matches.getItems().setAll(rows.stream().map(HistoryData.Summary.class::cast).toList());
                    status.setText(rows.isEmpty() ? "Bạn chưa có trận đấu nào." : "Chọn một trận đã kết thúc để xem chi tiết.");
                    UiTheme.status(status,"info");
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
                    details.setText(text.toString()); renderOverview(d);
                    status.setText("Đã tải kết quả."); UiTheme.status(status,"success");
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
            case HISTORY_FAILED -> { status.setText("NOT_FOUND_OR_UNFINISHED".equals(m.getData()) ? "Trận chưa kết thúc hoặc không thuộc lịch sử của bạn." : "Không tải/lưu được lịch sử. Kiểm tra MySQL và chạy history.sql."); UiTheme.status(status,"error"); }
            default -> { }
        }
    }
    private static String time(Long value) { return HistoryData.formatTime(value); }
}
