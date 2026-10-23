package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.UserInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.Locale;
import java.util.Objects;

public final class HomeView extends VBox {
    private final AvatarView avatar;
    private final Label greeting = new Label();
    private final Label avatarName = new Label();
    private final Button playButton;
    private final Button historyButton;
    private final Button profileButton;
    private final Button logoutButton = new Button("Đăng xuất");

    public HomeView(UserInfo user, Runnable play, Runnable history, Runnable profile, Runnable logout) {
        UiTheme.apply(this);
        getStyleClass().add("content-card");
        setSpacing(22);
        setPadding(new Insets(28));
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        avatar = new AvatarView(user.getAvatarKey(), 82);
        greeting.getStyleClass().add("page-title");
        Label welcome = new Label("Chào mừng trở lại Starry Studio");
        welcome.getStyleClass().add("subtle-text");
        avatarName.getStyleClass().add("avatar-name");
        VBox identity = new VBox(4, greeting, welcome, avatarName);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        ArtworkCard painting = new ArtworkCard(
                "/images/View-of-the-Sea-at-Scheveningen.jpg", "View of the Sea at Scheveningen", 330, 104);
        HBox header = new HBox(16, avatar, identity, headerSpacer, painting);
        header.setAlignment(Pos.CENTER_LEFT);

        Label sectionTitle = new Label("Bạn muốn làm gì hôm nay?");
        sectionTitle.getStyleClass().add("section-title");
        Label sectionSubtitle = new Label("Chọn một hoạt động để bắt đầu hành trình dưới bầu trời đầy sao.");
        sectionSubtitle.getStyleClass().add("subtle-text");

        playButton = action("CHƠI NGAY", "Vào phòng chơi", "Chọn phòng, gặp bạn bè và bắt đầu vẽ",
                "/images/Cafe-Terrace-at-Night-1888.jpg", "dashboard-primary", play);
        historyButton = action("NHẬT KÝ", "Lịch sử trận đấu", "Xem lại điểm số, lượt đoán và tranh đã vẽ",
                "/images/Starry-Night-Over-the-Rhone-1888.jpg", "dashboard-secondary", history);
        profileButton = action("BỘ SƯU TẬP", "Hồ sơ & avatar", "Chọn hình đại diện từ bộ tranh Van Gogh",
                "/images/Almond-Blossoms-1890.jpg", "dashboard-accent", profile);
        HBox actions = new HBox(16, playButton, historyButton, profileButton);
        actions.setMinHeight(280);
        VBox.setVgrow(actions, Priority.ALWAYS);
        for (Button button : new Button[]{playButton, historyButton, profileButton}) {
            HBox.setHgrow(button, Priority.ALWAYS);
        }

        Label quote = new Label("“Tôi thường nghĩ rằng đêm sống động và nhiều màu sắc hơn ban ngày.”");
        quote.getStyleClass().add("home-quote");
        Label author = new Label("— Vincent van Gogh");
        author.getStyleClass().add("home-quote-author");
        VBox quoteCard = new VBox(8, quote, author);
        quoteCard.getStyleClass().add("home-quote-card");
        quoteCard.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(quoteCard, Priority.ALWAYS);

        logoutButton.setOnAction(event -> logout.run());
        logoutButton.getStyleClass().add("danger-button");
        HBox footer = new HBox(logoutButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(header, new javafx.scene.control.Separator(), sectionTitle,
                sectionSubtitle, actions, quoteCard, footer);
        updateUser(user);
    }

    private Button action(String label, String title, String description, String imageResource,
                          String style, Runnable action) {
        Label eyebrow = new Label(label); eyebrow.getStyleClass().add("dashboard-eyebrow");
        Label heading = new Label(title); heading.getStyleClass().add("dashboard-title");
        Label text = new Label(description); text.getStyleClass().add("dashboard-description");
        text.setWrapText(true);
        Label open = new Label("Mở ngay  →"); open.getStyleClass().add("dashboard-open");
        VBox graphic = new VBox(8, eyebrow, heading, text, open);
        graphic.setAlignment(Pos.CENTER);
        graphic.getStyleClass().add("dashboard-copy-panel");
        graphic.setMaxHeight(Region.USE_PREF_SIZE);
        Button button = new Button();
        button.setGraphic(graphic);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setAlignment(Pos.CENTER);
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        button.setMinHeight(280);
        button.getStyleClass().addAll("dashboard-card", style);
        URL image = Objects.requireNonNull(HomeView.class.getResource(imageResource), imageResource);
        button.setStyle("-fx-background-image: url('" + image.toExternalForm() + "');"
                + "-fx-background-size: cover;"
                + "-fx-background-position: center center;"
                + "-fx-background-repeat: no-repeat;");
        button.setAccessibleText(title);
        button.setOnAction(event -> action.run());
        return button;
    }

    public void updateUser(UserInfo user) {
        greeting.setText("Xin chào, " + user.getUsername().toUpperCase(Locale.ROOT));
        avatar.setAvatar(user.getAvatarKey());
        avatarName.setText("Avatar: " + AvatarView.title(user.getAvatarKey()));
    }

    public void setBusy(boolean busy) {
        playButton.setDisable(busy);
        historyButton.setDisable(busy);
        profileButton.setDisable(busy);
        logoutButton.setDisable(busy);
    }
}
