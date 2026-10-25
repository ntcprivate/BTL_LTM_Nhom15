package com.nhom15.drawguess.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.StackPane;

// Ô mật khẩu có nút con mắt ở cuối ô để hiện/ẩn chữ đã gõ.
// Vẫn là một PasswordField duy nhất (giữ con trỏ, focus, nhãn và chặn copy), chỉ đổi cách hiển thị chữ.
public class PasswordInput extends StackPane {
    private final PasswordField field = new PasswordField();
    private final ToggleButton toggle = new ToggleButton();
    private boolean revealed;

    public PasswordInput(String prompt) {
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        field.getStyleClass().add("password-input");
        field.setSkin(createSkin());
        toggle.getStyleClass().add("password-toggle");
        toggle.selectedProperty().addListener((observable, before, shown) -> reveal(shown));
        reveal(false);
        StackPane.setAlignment(toggle, Pos.CENTER_RIGHT);
        StackPane.setMargin(toggle, new Insets(0, 6, 0, 0));
        setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(field, toggle);
    }

    private TextFieldSkin createSkin() {
        return new TextFieldSkin(field) {
            @Override protected String maskText(String text) {
                return revealed ? text : super.maskText(text);
            }
        };
    }

    private void reveal(boolean shown) {
        if (revealed != shown) {
            revealed = shown;
            // Skin chỉ tính lại chữ hiển thị khi được tạo mới; con trỏ và nội dung vẫn giữ nguyên.
            int caret = field.getCaretPosition();
            field.setSkin(createSkin());
            field.positionCaret(caret);
        }
        String action = shown ? "Ẩn mật khẩu" : "Hiện mật khẩu";
        toggle.setGraphic(Icons.icon(shown ? Icons.Glyph.EYE_OFF : Icons.Glyph.EYE, 18, toggle.textFillProperty()));
        toggle.setAccessibleText(action);
        toggle.setTooltip(new Tooltip(action));
    }

    public PasswordField field() {
        return field;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(boolean shown) {
        toggle.setSelected(shown);
    }

    public String getText() {
        return field.getText();
    }

    public void clear() {
        field.clear();
        toggle.setSelected(false);
    }
}
