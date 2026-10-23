package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.AvatarCatalog;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.net.URL;
import java.util.Map;
import java.util.Objects;

public final class AvatarView extends StackPane {
    private static final Map<String, String> RESOURCES = Map.ofEntries(
            Map.entry("starry-night", "/images/The-Starry-Night-1889.jpg"),
            Map.entry("rhone-night", "/images/Starry-Night-Over-the-Rhone-1888.jpg"),
            Map.entry("cafe-night", "/images/Cafe-Terrace-at-Night-1888.jpg"),
            Map.entry("almond-blossoms", "/images/Almond-Blossoms-1890.jpg"),
            Map.entry("irises", "/images/Irises-1889.jpg"),
            Map.entry("saint-remy", "/images/Landscape-from-Saint-Remy-1889.jpg"),
            Map.entry("langlois-bridge", "/images/The-Langlois-Bridge-at-Arles-1888.jpg"),
            Map.entry("wheatfield", "/images/Wheatfield-with-Crows-1890.jpg"),
            Map.entry("scheveningen-sea", "/images/View-of-the-Sea-at-Scheveningen.jpg")
    );
    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("starry-night", "Đêm đầy sao"),
            Map.entry("rhone-night", "Đêm trên sông Rhône"),
            Map.entry("cafe-night", "Quán cà phê đêm"),
            Map.entry("almond-blossoms", "Hoa hạnh nhân"),
            Map.entry("irises", "Hoa diên vĩ"),
            Map.entry("saint-remy", "Saint-Rémy"),
            Map.entry("langlois-bridge", "Cầu Langlois"),
            Map.entry("wheatfield", "Cánh đồng lúa mì"),
            Map.entry("scheveningen-sea", "Biển Scheveningen")
    );

    private final ImageView image = new ImageView();
    private final double size;
    private String avatarKey;

    public AvatarView(String avatarKey, double size) {
        this.size = size;
        image.setFitWidth(size);
        image.setFitHeight(size);
        image.setPreserveRatio(false);
        image.setSmooth(true);
        Circle clip = new Circle(size / 2, size / 2, size / 2 - 3);
        image.setClip(clip);
        Circle frame = new Circle(size / 2 - 2);
        frame.getStyleClass().add("avatar-frame");
        frame.setMouseTransparent(true);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getChildren().addAll(image, frame);
        setAvatar(avatarKey);
    }

    public void setAvatar(String key) {
        avatarKey = AvatarCatalog.normalize(key);
        URL resource = Objects.requireNonNull(AvatarView.class.getResource(RESOURCES.get(avatarKey)));
        Image source = new Image(resource.toExternalForm(), false);
        image.setImage(source);
        double side = Math.min(source.getWidth(), source.getHeight());
        image.setViewport(new Rectangle2D(
                (source.getWidth() - side) / 2,
                (source.getHeight() - side) / 2,
                side,
                side));
        setAccessibleText(title(avatarKey));
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public static String title(String key) {
        return TITLES.getOrDefault(AvatarCatalog.normalize(key), "Đêm đầy sao");
    }
}
