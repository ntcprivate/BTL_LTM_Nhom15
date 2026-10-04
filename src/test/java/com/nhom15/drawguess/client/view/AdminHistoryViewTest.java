package com.nhom15.drawguess.client.view;
import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.common.protocol.CatalogData.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="drawguess.ui",matches="true")
class AdminHistoryViewTest {
    @BeforeAll static void init() throws Exception {
        CompletableFuture<Void> ready=new CompletableFuture<>();
        Platform.startup(()-> { Platform.setImplicitExit(false); ready.complete(null); }); ready.get(15,TimeUnit.SECONDS);
    }
    private static void ui(Runnable task) throws Exception {
        CompletableFuture<Void> done=new CompletableFuture<>(); Platform.runLater(()-> { try { task.run(); done.complete(null); } catch(Throwable e) { done.completeExceptionally(e); } }); done.get(15,TimeUnit.SECONDS);
    }
    @AfterEach void closeWindows() throws Exception {
        ui(()-> { for(var window:List.copyOf(javafx.stage.Window.getWindows())) window.hide(); });
    }
    private static void snapshot(Parent view,String filename) {
        try {
            if(view.getScene()==null) {
                var stage=new javafx.stage.Stage(); stage.setOpacity(0); stage.setScene(new Scene(view,1000,760)); stage.show();
            } view.applyCss(); view.layout();
            for(var node:view.lookupAll(".scroll-pane")) {
                if(node instanceof ScrollPane scroll && scroll.getContent() instanceof javafx.scene.layout.Region content) {
                    content.resize(scroll.getViewportBounds().getWidth(),content.prefHeight(scroll.getViewportBounds().getWidth()));
                    content.applyCss(); content.layout();
                }
            }
            WritableImage image=view.snapshot(null,null);
            BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<png.getHeight();y++) for(int x=0;x<png.getWidth();x++) png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            Files.createDirectories(Path.of("target/qa")); ImageIO.write(png,"png",Path.of("target/qa",filename).toFile());
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    @Test void adminLoadsCatalogFiltersWordsAndSendsEdit() throws Exception {
        ui(()-> {
            List<Message> sent=new ArrayList<>(); AdminView view=new AdminView(new UserInfo(1,"admin","ADMIN"),sent::add,()->{});
            assertEquals(MessageType.GET_CATALOG,sent.getFirst().getType());
            view.handleMessage(new Message(MessageType.CATALOG,new Catalog(List.of(new Category(1,"Động vật","Chủ đề động vật"),new Category(2,"Đồ ăn","")),List.of(new Word(1,1,"Con mèo","EASY"),new Word(2,2,"Bánh mì","EASY")))));
            snapshot(view,"admin.png");
            ListView<?> categories=(ListView<?>)view.lookupAll(".list-view").stream().filter(n->((ListView<?>)n).getItems().stream().anyMatch(Category.class::isInstance)).findFirst().orElseThrow();
            categories.getSelectionModel().select(0);
            ListView<?> words=(ListView<?>)view.lookupAll(".list-view").stream().filter(n->((ListView<?>)n).getItems().stream().anyMatch(Word.class::isInstance)).findFirst().orElseThrow();
            assertEquals(1,words.getItems().size());
            TextField name=(TextField)view.lookupAll(".text-field").stream().filter(n->n instanceof TextField t && t.getPromptText().startsWith("Từ khóa")).findFirst().orElseThrow(); name.setText("Con chó");
            Parent box=name.getParent(); Button add=(Button)box.lookupAll(".button").stream().filter(n->((Button)n).getText().equals("Thêm")).findFirst().orElseThrow(); add.fire();
            CatalogData.Edit edit=assertInstanceOf(CatalogData.Edit.class,sent.getLast().getData()); assertEquals("WORD",edit.entity()); assertEquals("Con chó",edit.name()); assertEquals(1,edit.categoryId());
            view.handleMessage(new Message(MessageType.CATALOG_FAILED,"DUPLICATE_OR_INVALID_REFERENCE")); assertFalse(view.isDisabled()); snapshot(view,"admin.png");
        });
    }
    @Test void historySelectionLoadsAndDisplaysRoundAndGuessDetails() throws Exception {
        ui(()-> {
            List<Message> sent=new ArrayList<>(); HistoryView view=new HistoryView(sent::add,()->{});
            String id=UUID.randomUUID().toString(); long at=System.currentTimeMillis();
            view.handleMessage(new Message(MessageType.HISTORY_LIST,List.of(new HistoryData.Summary(id,1,"Động vật",at,at+60000,"COMPLETED",3,1))));
            snapshot(view,"history.png"); ((ListView<?>)view.lookup(".list-view")).getSelectionModel().select(0);
            assertEquals(MessageType.GET_HISTORY_DETAIL,sent.getLast().getType()); assertEquals(id,sent.getLast().getData());
            var round=new HistoryData.Round(0,2,"người_vẽ","Con mèo","server-data/drawings/"+id+"/2.png",at+1000,at+30000,List.of(new HistoryData.Guess(1,"người_chơi","Con mèo",true,at+2000,1)));
            view.handleMessage(new Message(MessageType.HISTORY_DETAIL,new HistoryData.Detail(id,1,"Động vật",at,at+60000,"COMPLETED",List.of(new ScoreEntry(1,"người_chơi",3,1,1,true)),List.of(round))));
            assertEquals(MessageType.GET_HISTORY_IMAGE, sent.getLast().getType());
            assertEquals(new HistoryData.ImageRequest(id,0), sent.getLast().getData());
            view.handleMessage(new Message(MessageType.HISTORY_IMAGE, new HistoryData.DrawingImage(id,0, samplePng())));
            ImageView image=(ImageView)view.lookup(".history-image"); assertNotNull(image.getImage()); assertEquals(500,image.getImage().getWidth());
            view.handleMessage(new Message(MessageType.HISTORY_IMAGE_FAILED,new HistoryData.ImageFailure(UUID.randomUUID().toString(),0,"IMAGE_NOT_AVAILABLE")));
            assertNotNull(image.getImage());
            Label text=(Label)view.lookup(".history-details"); assertTrue(text.getText().contains("Con mèo")); assertTrue(text.getText().contains("Hạng 1")); assertTrue(text.getText().contains("người_vẽ")); var delay=new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
            delay.setOnFinished(e->snapshot(view,"history.png")); delay.play();
        });
        CompletableFuture<Void> pulse=new CompletableFuture<>();
        Platform.runLater(()-> { var delay=new javafx.animation.PauseTransition(javafx.util.Duration.millis(400)); delay.setOnFinished(e->pulse.complete(null)); delay.play(); });
        pulse.get(15,TimeUnit.SECONDS);
    }
    private static byte[] samplePng() {
        try {
            BufferedImage image=new BufferedImage(500,500,BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();
            try {
                g.setColor(java.awt.Color.WHITE); g.fillRect(0,0,500,500);
                g.setColor(java.awt.Color.BLUE); g.setStroke(new java.awt.BasicStroke(8));
                g.drawOval(125,125,250,250); g.drawLine(180,200,210,165); g.drawLine(290,165,320,200);
                g.drawOval(185,220,15,15); g.drawOval(300,220,15,15); g.drawArc(200,250,100,50,180,180);
            } finally { g.dispose(); }
            var bytes=new java.io.ByteArrayOutputStream(); ImageIO.write(image,"png",bytes); return bytes.toByteArray();
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    @Test void changingRoundIgnoresOldResponsesAndMissingImageCanBeRetried() throws Exception {
        ui(()-> {
            List<Message> sent=new ArrayList<>(); HistoryView view=new HistoryView(sent::add,()->{});
            String id=UUID.randomUUID().toString(); long at=System.currentTimeMillis();
            view.handleMessage(new Message(MessageType.HISTORY_LIST,List.of(new HistoryData.Summary(id,1,"Chủ đề",at,at+1000,"COMPLETED",0,1))));
            snapshot(view,"history-switch.png"); ((ListView<?>)view.lookup(".list-view")).getSelectionModel().select(0);
            var first=new HistoryData.Round(0,1,"p1","w1","path",at,at+1000,List.of());
            var second=new HistoryData.Round(1,2,"p2","w2","path",at,at+1000,List.of());
            view.handleMessage(new Message(MessageType.HISTORY_DETAIL,new HistoryData.Detail(id,1,"Chủ đề",at,at+1000,"COMPLETED",List.of(),List.of(first,second))));
            ComboBox<?> rounds=(ComboBox<?>)view.lookup(".history-rounds"); rounds.getSelectionModel().select(1);
            assertEquals(new HistoryData.ImageRequest(id,1),sent.getLast().getData());
            view.handleMessage(new Message(MessageType.HISTORY_IMAGE,new HistoryData.DrawingImage(id,0,samplePng())));
            ImageView image=(ImageView)view.lookup(".history-image"); assertNull(image.getImage());
            view.handleMessage(new Message(MessageType.HISTORY_IMAGE_FAILED,new HistoryData.ImageFailure(id,1,"IMAGE_NOT_AVAILABLE")));
            Label message=(Label)view.lookup(".history-image-status"); assertTrue(message.getText().contains("Không tìm thấy"));
            Button retry=(Button)view.lookupAll(".button").stream().filter(n->((Button)n).getText().equals("Tải lại ảnh")).findFirst().orElseThrow();
            assertFalse(retry.isDisabled()); retry.fire(); assertEquals(new HistoryData.ImageRequest(id,1),sent.getLast().getData());
            view.handleMessage(new Message(MessageType.HISTORY_IMAGE,new HistoryData.DrawingImage(id,1,samplePng())));
            assertNotNull(image.getImage());
        });
    }
}
