package com.nhom15.drawguess.client.view;
import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.common.protocol.CatalogData.*;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;
import java.util.function.Consumer;
public class AdminView extends VBox {
    private final Consumer<Message> send;
    private final ListView<Category> categories=new ListView<>();
    private final ListView<Word> words=new ListView<>();
    private final TextField categoryName=new TextField(),description=new TextField(),wordName=new TextField();
    private final ComboBox<String> difficulty=new ComboBox<>();
    private final Label status=new Label();
    private List<Word> allWords=List.of();
    private final HBox actions=new HBox(10);
    public AdminView(UserInfo user,Consumer<Message> send,Runnable logout) {
        this.send=send; setPadding(new Insets(24)); setSpacing(12);
        difficulty.getItems().setAll("EASY","MEDIUM","HARD"); difficulty.setValue("EASY");
        categoryName.setPromptText("Tên chủ đề (tối đa 100 ký tự)"); description.setPromptText("Mô tả (tối đa 255 ký tự)"); wordName.setPromptText("Từ khóa (tối đa 100 ký tự)");
        Button refresh=new Button("Làm mới"),exit=new Button("Đăng xuất"); refresh.setOnAction(e->load()); exit.setOnAction(e->logout.run()); actions.getChildren().addAll(refresh,exit);
        categories.getSelectionModel().selectedItemProperty().addListener((o,old,c)-> { categoryName.setText(c==null ? "" : c.name()); description.setText(c==null || c.description()==null ? "" : c.description()); filterWords(); });
        words.getSelectionModel().selectedItemProperty().addListener((o,old,w)-> { wordName.setText(w==null ? "" : w.content()); difficulty.setValue(w==null ? "EASY" : w.difficulty()); });
        VBox catBox=new VBox(10,new Label("Chủ đề"),categories,categoryName,description,buttons("CATEGORY"));
        VBox wordBox=new VBox(10,new Label("Từ khóa của chủ đề đang chọn"),words,wordName,difficulty,buttons("WORD"));
        HBox columns=new HBox(20,catBox,wordBox); HBox.setHgrow(catBox,Priority.ALWAYS); HBox.setHgrow(wordBox,Priority.ALWAYS); catBox.setMaxWidth(Double.MAX_VALUE); wordBox.setMaxWidth(Double.MAX_VALUE); VBox.setVgrow(columns,Priority.ALWAYS); VBox.setVgrow(categories,Priority.ALWAYS); VBox.setVgrow(words,Priority.ALWAYS);
        status.setWrapText(true); getChildren().addAll(new Label("Quản trị chủ đề / từ khóa — "+user.getUsername()),actions,columns,status); load();
    }
    private HBox buttons(String entity) {
        Button add=new Button("Thêm"),update=new Button("Sửa mục chọn"),delete=new Button("Xóa mục chọn");
        add.setOnAction(e->edit(entity,"CREATE")); update.setOnAction(e->edit(entity,"UPDATE")); delete.setOnAction(e->edit(entity,"DELETE"));
        return new HBox(8,add,update,delete);
    }
    private void edit(String entity,String action) {
        Category c=categories.getSelectionModel().getSelectedItem(); Word w=words.getSelectionModel().getSelectedItem(); boolean cat="CATEGORY".equals(entity);
        int id=cat ? (c==null ? 0 : c.id()) : (w==null ? 0 : w.id());
        if((!"CREATE".equals(action) && id==0) || (!cat && c==null)) { status.setText("Hãy chọn mục cần thao tác và chủ đề của từ khóa."); return; }
        String name=(cat ? categoryName : wordName).getText().trim();
        if(!"DELETE".equals(action) && (name.isBlank() || name.length()>100 || (cat && description.getText().length()>255))) { status.setText("Tên cần có 1–100 ký tự; mô tả tối đa 255 ký tự."); return; }
        if("DELETE".equals(action)) {
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,cat ? "Xóa chủ đề này và toàn bộ từ khóa? Lịch sử đã lưu vẫn được giữ." : "Xóa từ khóa đang chọn?",ButtonType.OK,ButtonType.CANCEL);
            if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK) return;
        }
        status.setText("Đang lưu..."); setDisable(true);
        send.accept(new Message(MessageType.EDIT_CATALOG,new Edit(entity,action,id,c==null ? 0 : c.id(),name,description.getText(),difficulty.getValue())));
    }
    private void load() { status.setText("Đang tải..."); send.accept(new Message(MessageType.GET_CATALOG)); }
    private void filterWords() { Category c=categories.getSelectionModel().getSelectedItem(); words.getItems().setAll(allWords.stream().filter(w->c!=null && w.categoryId()==c.id()).toList()); }
    public void handleMessage(Message m) {
        switch(m.getType()) {
            case CATALOG -> { if(m.getData() instanceof Catalog data) {
                Category previous=categories.getSelectionModel().getSelectedItem(); allWords=data.words(); categories.getItems().setAll(data.categories());
                if(previous!=null) data.categories().stream().filter(c->c.id()==previous.id()).findFirst().ifPresent(c->categories.getSelectionModel().select(c));
                filterWords(); setDisable(false); status.setText("Đã tải "+data.categories().size()+" chủ đề, "+data.words().size()+" từ khóa.");
            } }
            case CATALOG_SAVED -> { setDisable(false); status.setText("Đã lưu thay đổi."); }
            case CATALOG_FAILED -> { setDisable(false); status.setText(switch(String.valueOf(m.getData())) {
                case "FORBIDDEN" -> "Tài khoản không có quyền quản trị.";
                case "DUPLICATE_OR_INVALID_REFERENCE" -> "Tên đã tồn tại hoặc chủ đề không hợp lệ.";
                case "DATABASE_ERROR" -> "Không truy cập được MySQL. Kiểm tra cấu hình cơ sở dữ liệu.";
                default -> "Dữ liệu không hợp lệ hoặc mục đã bị xóa. Hãy làm mới và thử lại.";
            }); }
            default -> { }
        }
    }
    public void setLoggingOut() { setDisable(true); status.setText("Đang đăng xuất..."); }
}
