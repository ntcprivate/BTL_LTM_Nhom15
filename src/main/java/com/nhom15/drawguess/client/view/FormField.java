package com.nhom15.drawguess.client.view;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

// Một ô nhập trong form: nhãn nối với ô bằng setLabelFor (trình đọc màn hình đọc được tên ô)
// và dòng báo lỗi nằm ngay dưới ô, tự ẩn khi người dùng sửa lại nội dung.
public class FormField extends VBox {
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private final Control input;
    private final Label error = new Label();

    public FormField(String text, Control input) {
        this(text, input, input);
    }

    // `content` là phần hiển thị (ví dụ ô mật khẩu kèm nút con mắt), `input` là ô nhận bàn phím.
    public FormField(String text, Control input, Node content) {
        super(6);
        this.input = input;
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        label.setLabelFor(input);
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        error.setMinHeight(Region.USE_PREF_SIZE);
        Icons.attach(error, Icons.Glyph.ALERT, 13);
        setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(label, content, error);
        clearError();
        if (input instanceof TextInputControl editable) {
            editable.textProperty().addListener((observable, before, after) -> clearError());
        }
    }

    public void showError(String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
        input.pseudoClassStateChanged(INVALID, true);
        input.setAccessibleHelp(message);
    }

    public void clearError() {
        error.setText("");
        error.setVisible(false);
        error.setManaged(false);
        input.pseudoClassStateChanged(INVALID, false);
        input.setAccessibleHelp(null);
    }

    public boolean hasError() {
        return error.isVisible();
    }

    public void focusInput() {
        input.requestFocus();
    }
}
