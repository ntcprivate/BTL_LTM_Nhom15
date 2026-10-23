package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.AvatarCatalog;
import com.nhom15.drawguess.common.protocol.UserInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public final class AvatarPickerView extends VBox {
    private final ToggleGroup choices = new ToggleGroup();
    private final AvatarView preview;
    private final Label previewName = new Label();
    private final Label status = new Label("Chọn một bức tranh làm hình đại diện của bạn.");
    private final Button saveButton = new Button("Lưu avatar");
    private final Button backButton = new Button("Về trang chủ");

    public AvatarPickerView(UserInfo user, Runnable save, Runnable back) {
        UiTheme.apply(this);
        getStyleClass().add("content-card");
        setSpacing(18);
        setPadding(new Insets(26));
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Label title = new Label("Chọn avatar Van Gogh");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Avatar được lưu vào tài khoản và hiển thị trong phòng chờ.");
        subtitle.getStyleClass().add("subtle-text");
        VBox heading = new VBox(4, title, subtitle);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        backButton.setOnAction(event -> back.run());
        backButton.getStyleClass().add("ghost-button");
        HBox header = new HBox(14, heading, headerSpacer, backButton);
        header.setAlignment(Pos.CENTER_LEFT);

        preview = new AvatarView(user.getAvatarKey(), 112);
        Label previewCaption = new Label("Avatar đang chọn");
        previewCaption.getStyleClass().add("field-label");
        previewName.getStyleClass().add("section-title");
        Label hint = new Label("Bạn có thể thay đổi bất cứ lúc nào khi không ở trong phòng.");
        hint.getStyleClass().add("subtle-text");
        VBox previewText = new VBox(5, previewCaption, previewName, hint);
        HBox previewCard = new HBox(18, preview, previewText);
        previewCard.setAlignment(Pos.CENTER_LEFT);
        previewCard.getStyleClass().add("avatar-preview-card");

        TilePane gallery = new TilePane();
        gallery.setHgap(12);
        gallery.setVgap(12);
        gallery.setPrefColumns(5);
        gallery.setTileAlignment(Pos.CENTER);
        for (String key : AvatarCatalog.KEYS) {
            AvatarView thumbnail = new AvatarView(key, 68);
            Label name = new Label(AvatarView.title(key));
            name.getStyleClass().add("avatar-option-name");
            VBox graphic = new VBox(8, thumbnail, name);
            graphic.setAlignment(Pos.CENTER);
            ToggleButton option = new ToggleButton();
            option.setGraphic(graphic);
            option.setUserData(key);
            option.setToggleGroup(choices);
            option.getStyleClass().add("avatar-option");
            option.setOnAction(event -> updatePreview());
            if (key.equals(user.getAvatarKey())) {
                option.setSelected(true);
            }
            gallery.getChildren().add(option);
        }

        saveButton.setOnAction(event -> save.run());
        saveButton.getStyleClass().add("primary-button");
        UiTheme.status(status, "info");
        HBox footer = new HBox(12, saveButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(header, previewCard, gallery, status, footer);
        VBox.setVgrow(gallery, Priority.ALWAYS);
        updatePreview();
    }

    private void updatePreview() {
        String key = getSelectedAvatar();
        preview.setAvatar(key);
        previewName.setText(AvatarView.title(key));
    }

    public String getSelectedAvatar() {
        return choices.getSelectedToggle() == null
                ? AvatarCatalog.DEFAULT : String.valueOf(choices.getSelectedToggle().getUserData());
    }

    public void updateUser(UserInfo user) {
        choices.getToggles().stream().filter(toggle -> user.getAvatarKey().equals(toggle.getUserData()))
                .findFirst().ifPresent(toggle -> choices.selectToggle(toggle));
        updatePreview();
    }

    public void setBusy(boolean busy) {
        saveButton.setDisable(busy);
        backButton.setDisable(busy);
        choices.getToggles().forEach(toggle -> ((ToggleButton) toggle).setDisable(busy));
    }

    public void showMessage(String message, boolean success) {
        status.setText(message);
        UiTheme.status(status, success ? "success" : "error");
    }

    public void showInfo(String message) {
        status.setText(message);
        UiTheme.status(status, "info");
    }
}
