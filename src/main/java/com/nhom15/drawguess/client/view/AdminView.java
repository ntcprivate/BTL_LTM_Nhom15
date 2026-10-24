package com.nhom15.drawguess.client.view;
import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.common.protocol.CatalogData.*;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
public class AdminView extends VBox {
    private final Consumer<Message> send;
    private final ListView<Category> categories=new ListView<>();
    private final ListView<Word> words=new ListView<>();
    private final TextField categoryName=new TextField(),description=new TextField(),wordName=new TextField();
    private final ComboBox<String> difficulty=new ComboBox<>();
    private static final Map<String,String> DIFFICULTY_LABELS=Map.of("EASY","Dễ","MEDIUM","Trung bình","HARD","Khó");
    private final Label status=new Label();
    // Thông báo hiện ngay dưới khối vừa thao tác (chủ đề hoặc từ khóa).
    private final Label categoryStatus=new Label(),wordStatus=new Label();
    private Label lastEditStatus;
    private List<Word> allWords=List.of();
    private final HBox actions=new HBox(10);
    public AdminView(UserInfo user,Consumer<Message> send,Runnable logout) {
        this.send=send;
        UiTheme.apply(this);
        getStyleClass().add("content-card");
        setPadding(new Insets(22)); setSpacing(14);
        setMaxWidth(Double.MAX_VALUE); setMaxHeight(Double.MAX_VALUE);
        difficulty.getItems().setAll("EASY","MEDIUM","HARD"); difficulty.setValue("EASY");
        difficulty.setConverter(new StringConverter<>() {
            @Override public String toString(String value) { return value==null ? "" : DIFFICULTY_LABELS.getOrDefault(value,value); }
            @Override public String fromString(String text) { return DIFFICULTY_LABELS.entrySet().stream().filter(e->e.getValue().equals(text)).map(Map.Entry::getKey).findFirst().orElse(text); }
        });
        words.setCellFactory(list->new ListCell<>() {
            @Override protected void updateItem(Word w,boolean empty) {
                super.updateItem(w,empty);
                setText(empty || w==null ? null : w.content()+"  —  "+DIFFICULTY_LABELS.getOrDefault(w.difficulty(),w.difficulty()));
            }
        });
        categoryName.setPromptText("Tên chủ đề (tối đa 100 ký tự)"); description.setPromptText("Mô tả (tối đa 255 ký tự)"); wordName.setPromptText("Từ khóa (tối đa 100 ký tự)");
        Button refresh=new Button("Làm mới"),exit=new Button("Đăng xuất");
        refresh.getStyleClass().add("secondary-button"); exit.getStyleClass().add("danger-button");
        refresh.setOnAction(e->load()); exit.setOnAction(e->logout.run());
        Region actionSpacer=new Region(); HBox.setHgrow(actionSpacer,Priority.ALWAYS);
        actions.getChildren().addAll(refresh,actionSpacer,exit);
        categories.getSelectionModel().selectedItemProperty().addListener((o,old,c)-> { categoryName.setText(c==null ? "" : c.name()); description.setText(c==null || c.description()==null ? "" : c.description()); filterWords(); });
        words.getSelectionModel().selectedItemProperty().addListener((o,old,w)-> { wordName.setText(w==null ? "" : w.content()); difficulty.setValue(w==null ? "EASY" : w.difficulty()); });
        Label categoryTitle=new Label("Chủ đề"); categoryTitle.getStyleClass().add("section-title");
        Label wordTitle=new Label("Từ khóa của chủ đề đang chọn"); wordTitle.getStyleClass().add("section-title");
        for(Label cardStatus:List.of(categoryStatus,wordStatus)) { cardStatus.setWrapText(true); cardStatus.setVisible(false); cardStatus.setManaged(false); cardStatus.getStyleClass().add("card-status"); }
        VBox catBox=new VBox(10,categoryTitle,categories,field("Tên chủ đề",categoryName),field("Mô tả",description),buttons("CATEGORY"),categoryStatus);
        VBox wordBox=new VBox(10,wordTitle,words,field("Từ khóa",wordName),field("Độ khó",difficulty),buttons("WORD"),wordStatus);
        catBox.getStyleClass().add("sidebar-card"); wordBox.getStyleClass().add("sidebar-card");
        HBox columns=new HBox(20,catBox,wordBox); HBox.setHgrow(catBox,Priority.ALWAYS); HBox.setHgrow(wordBox,Priority.ALWAYS); catBox.setMaxWidth(Double.MAX_VALUE); wordBox.setMaxWidth(Double.MAX_VALUE); VBox.setVgrow(columns,Priority.ALWAYS); VBox.setVgrow(categories,Priority.ALWAYS); VBox.setVgrow(words,Priority.ALWAYS);
        Label title=new Label("Quản trị nội dung"); title.getStyleClass().add("page-title");
        Label subtitle=new Label("Chủ đề, từ khóa và độ khó • "+user.getUsername()); subtitle.getStyleClass().add("subtle-text");
        VBox heading=new VBox(3,title,subtitle);
        Region headerSpacer=new Region(); HBox.setHgrow(headerSpacer,Priority.ALWAYS);
        ArtworkCard painting=new ArtworkCard("/images/Irises-1889.jpg","Irises • 1889",280,82);
        HBox header=new HBox(16,heading,headerSpacer,painting); header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        status.setWrapText(true); UiTheme.status(status,"info");
        getChildren().addAll(header,actions,columns,status); load();
    }
    private VBox field(String text,Control input) {
        Label label=new Label(text); label.getStyleClass().add("field-label");
        input.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5,label,input);
    }
    private void showResult(Label target,String text,String kind) {
        Label label=target==null ? status : target;
        label.setVisible(true); label.setManaged(true);
        label.setText(text); UiTheme.status(label,kind);
    }
    private HBox buttons(String entity) {
        Button add=new Button("Thêm"),update=new Button("Cập nhật"),delete=new Button("Xóa");
        for(Button b:List.of(add,update,delete)) b.setMinWidth(Region.USE_PREF_SIZE);
        add.setTooltip(new Tooltip("Tạo mục mới từ nội dung đã nhập"));
        update.setTooltip(new Tooltip("Lưu nội dung đã nhập vào mục đang chọn trong danh sách"));
        delete.setTooltip(new Tooltip("Xóa mục đang chọn trong danh sách"));
        add.getStyleClass().add("primary-button"); update.getStyleClass().add("secondary-button"); delete.getStyleClass().add("danger-button");
        add.setOnAction(e->edit(entity,"CREATE")); update.setOnAction(e->edit(entity,"UPDATE")); delete.setOnAction(e->edit(entity,"DELETE"));
        return new HBox(8,add,update,delete);
    }
    private void edit(String entity,String action) {
        Category c=categories.getSelectionModel().getSelectedItem(); Word w=words.getSelectionModel().getSelectedItem(); boolean cat="CATEGORY".equals(entity);
        lastEditStatus=cat ? categoryStatus : wordStatus;
        int id=cat ? (c==null ? 0 : c.id()) : (w==null ? 0 : w.id());
        if((!"CREATE".equals(action) && id==0) || (!cat && c==null)) { showResult(lastEditStatus,"Hãy chọn mục cần thao tác và chủ đề của từ khóa.","error"); return; }
        String name=(cat ? categoryName : wordName).getText().trim();
        if(!"DELETE".equals(action) && (name.isBlank() || name.length()>100 || (cat && description.getText().length()>255))) { showResult(lastEditStatus,"Tên cần có 1–100 ký tự; mô tả tối đa 255 ký tự.","error"); return; }
        if("DELETE".equals(action)) {
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,cat ? "Xóa chủ đề này và toàn bộ từ khóa? Lịch sử đã lưu vẫn được giữ." : "Xóa từ khóa đang chọn?",ButtonType.OK,ButtonType.CANCEL);
            if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK) return;
        }
        showResult(lastEditStatus,"Đang lưu...","info"); setDisable(true);
        send.accept(new Message(MessageType.EDIT_CATALOG,new Edit(entity,action,id,c==null ? 0 : c.id(),name,description.getText(),difficulty.getValue())));
    }
    private void load() { status.setText("Đang tải..."); UiTheme.status(status,"info"); send.accept(new Message(MessageType.GET_CATALOG)); }
    private void filterWords() { Category c=categories.getSelectionModel().getSelectedItem(); words.getItems().setAll(allWords.stream().filter(w->c!=null && w.categoryId()==c.id()).toList()); }
    public void handleMessage(Message m) {
        switch(m.getType()) {
            case CATALOG -> { if(m.getData() instanceof Catalog data) {
                Category previous=categories.getSelectionModel().getSelectedItem(); allWords=data.words(); categories.getItems().setAll(data.categories());
                if(previous!=null) data.categories().stream().filter(c->c.id()==previous.id()).findFirst().ifPresent(c->categories.getSelectionModel().select(c));
                filterWords(); setDisable(false); status.setText("Đã tải "+data.categories().size()+" chủ đề, "+data.words().size()+" từ khóa."); UiTheme.status(status,"success");
            } }
            case CATALOG_SAVED -> { setDisable(false); showResult(lastEditStatus,"Đã lưu thay đổi.","success"); }
            case CATALOG_FAILED -> { setDisable(false); showResult(lastEditStatus,switch(String.valueOf(m.getData())) {
                case "FORBIDDEN" -> "Tài khoản không có quyền quản trị.";
                case "DUPLICATE_OR_INVALID_REFERENCE" -> "Tên đã tồn tại hoặc chủ đề không hợp lệ.";
                case "DATABASE_ERROR" -> "Không truy cập được MySQL. Kiểm tra cấu hình cơ sở dữ liệu.";
                default -> "Dữ liệu không hợp lệ hoặc mục đã bị xóa. Hãy làm mới và thử lại.";
            },"error"); }
            default -> { }
        }
    }
    public void setLoggingOut() { setDisable(true); status.setText("Đang đăng xuất..."); UiTheme.status(status,"info"); }
}
