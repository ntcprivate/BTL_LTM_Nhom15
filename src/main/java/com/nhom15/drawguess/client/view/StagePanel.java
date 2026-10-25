package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.RoomInfo;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Vòng đấu của phòng: mỗi vòng là một chủ đề. Chủ phòng thêm/bớt vòng và chọn chủ đề (hoặc "Ngẫu nhiên");
// người khác chỉ xem. Mỗi thay đổi gửi ngay lên server, server phát lại cho cả phòng.
public class StagePanel extends VBox {
    public static final String RANDOM = "Ngẫu nhiên";
    private final Label summary = new Label();
    private final Label note = new Label();
    private final VBox rows = new VBox(8);
    private final Button add = Icons.attach(new Button("Thêm vòng"), Icons.Glyph.PLUS, 15);
    private List<String> topics = List.of();
    private List<String> stages = RoomInfo.DEFAULT_STAGES;
    private boolean editable;
    private boolean rendering;
    private Consumer<List<String>> onChange = stages -> {};

    public StagePanel() {
        super(10);
        getStyleClass().add("stage-panel");
        Label title = Icons.attach(new Label("Vòng đấu"), Icons.Glyph.LAYERS, 18);
        title.getStyleClass().add("section-title");
        summary.getStyleClass().add("stage-summary");
        summary.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(8, title, spacer, summary);
        heading.setAlignment(Pos.CENTER_LEFT);
        note.getStyleClass().add("subtle-text");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        // Cửa sổ thấp thì danh sách vòng cuộn được, không đẩy khung chat ra ngoài.
        ScrollPane scroll = new ScrollPane(rows);
        scroll.getStyleClass().add("stage-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setFocusTraversable(false);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        add.getStyleClass().addAll("ghost-button", "stage-add");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(event -> {
            List<String> next = new ArrayList<>(stages);
            next.add("");
            change(next);
        });
        getChildren().addAll(heading, note, scroll, add);
        render();
    }

    public void setOnChange(Consumer<List<String>> action) {
        onChange = action;
    }

    // Chủ đề chọn được (server gửi khi vào phòng).
    public void setTopics(List<String> names) {
        topics = List.copyOf(names);
        render();
    }

    // Vòng đấu hiện tại của phòng; `editable` khi mình là chủ phòng và phòng chưa vào trận.
    public void show(List<String> roomStages, boolean canEdit) {
        if (roomStages.equals(stages) && canEdit == editable) { return; }
        stages = List.copyOf(roomStages);
        editable = canEdit;
        render();
    }

    public List<String> getStages() {
        return stages;
    }

    private void change(List<String> next) {
        stages = List.copyOf(next);
        render();
        onChange.accept(stages);
    }

    private void render() {
        rendering = true;
        rows.getChildren().clear();
        for (int index = 0; index < stages.size(); index++) {
            rows.getChildren().add(editable ? editableRow(index) : readOnlyRow(index));
        }
        summary.setText(stages.size() + "/" + RoomInfo.MAX_STAGES + " vòng");
        note.setText(editable ? "Mỗi vòng một chủ đề, điểm cộng dồn." : "Chủ phòng chọn chủ đề cho từng vòng.");
        add.setVisible(editable);
        add.setManaged(editable);
        add.setDisable(stages.size() >= RoomInfo.MAX_STAGES);
        rendering = false;
    }

    private HBox editableRow(int index) {
        ComboBox<String> topic = new ComboBox<>();
        topic.getItems().add(RANDOM);
        for (String name : topics) {
            if (name.equals(stages.get(index)) || !stages.contains(name)) { topic.getItems().add(name); }
        }
        String current = stages.get(index);
        if (!current.isBlank() && !topic.getItems().contains(current)) { topic.getItems().add(current); }
        topic.setValue(current.isBlank() ? RANDOM : current);
        topic.setMaxWidth(Double.MAX_VALUE);
        topic.setVisibleRowCount(8);
        topic.setAccessibleText("Chủ đề vòng " + (index + 1));
        HBox.setHgrow(topic, Priority.ALWAYS);
        topic.valueProperty().addListener((observable, before, value) -> {
            if (rendering || value == null) { return; }
            List<String> next = new ArrayList<>(stages);
            next.set(index, RANDOM.equals(value) ? "" : value);
            change(next);
        });
        Button remove = Icons.attach(new Button(), Icons.Glyph.CLOSE, 14);
        remove.getStyleClass().addAll("ghost-button", "stage-remove");
        remove.setAccessibleText("Bỏ vòng " + (index + 1));
        remove.setTooltip(new Tooltip("Bỏ vòng " + (index + 1)));
        remove.setDisable(stages.size() == 1);
        remove.setOnAction(event -> {
            List<String> next = new ArrayList<>(stages);
            next.remove(index);
            change(next);
        });
        HBox row = new HBox(8, number(index), topic, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stage-row");
        return row;
    }

    private HBox readOnlyRow(int index) {
        String current = stages.get(index);
        Label topic = new Label(current.isBlank() ? RANDOM : current);
        topic.getStyleClass().add("stage-topic");
        if (current.isBlank()) { topic.getStyleClass().add("stage-random"); }
        topic.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(10, number(index), topic);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("stage-row", "stage-row-locked");
        row.setAccessibleText("Vòng " + (index + 1) + ": " + topic.getText());
        return row;
    }

    private static Label number(int index) {
        Label number = new Label(String.valueOf(index + 1));
        number.getStyleClass().add("stage-number");
        number.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        number.setTooltip(new Tooltip("Vòng " + (index + 1)));
        return number;
    }
}
