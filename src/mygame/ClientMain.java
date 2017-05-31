package mygame;

import mygame.messages.HelloMessage;
import mygame.messages.MoveMessage;
import mygame.messages.ColorMessage;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.ZipLocator;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.network.Client;
import com.jme3.network.ClientStateListener;
import com.jme3.network.Message;
import com.jme3.network.Network;
import com.jme3.network.serializing.Serializer;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.io.IOException;
import java.util.Random;
import mygame.messages.PlayerConnectedMessage;

/**
 *
 * Baseado nos Tutoriais: https://jmonkeyengine.github.io/wiki/jme3.html
 * |--https://jmonkeyengine.github.io/wiki/jme3/advanced/networking.html
 * |--https://jmonkeyengine.github.io/wiki/jme3/beginner/hello_collision.html
 * |--https://jmonkeyengine.github.io/wiki/jme3/beginner/hello_input_system.html
 */
public class ClientMain extends SimpleApplication implements ClientStateListener {

    //ATRIBUTOS GLOBAIS
    Client myClient;
    ColorMessage colorMessage = new ColorMessage();
    MoveMessage movMessage = new MoveMessage();
    Geometry geom;
    Camera myCam;
    Spatial sceneModel;
    int velocidade = 10;
    public int ClientID = -1;

    public static void main(String[] args) {
        //Algumas placas de som não suportam a configuracao
        //padrao do AudioRenderer e ao iniciar aplicacao
        //aparece o seguinte erro: org.lwjgl.openal.OpenALException:Invalid Device
        //A configuração de som abaixo resolveu esse erro
        AppSettings s = new AppSettings(true);
        s.setAudioRenderer(AppSettings.LWJGL_OPENAL);
        ClientMain app = new ClientMain();
        app.setSettings(s);
        app.start(JmeContext.Type.Display);
    }

    public void adicionaJogador(String name, Vector3f loc, ColorRGBA color) {
        Box b = new Box(1, 1, 1);
        geom = new Geometry("PLAYER_" + name, b);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        geom.setMaterial(mat);
        geom.setLocalTranslation(loc);
        geom.scale(0.5f);
        rootNode.attachChild(geom);
    }

    private void adicionaCenario() {
        // Carrega a cena do arquivo zip e ajusta o tamanho
        assetManager.registerLocator("town.zip", ZipLocator.class);
        sceneModel = assetManager.loadModel("main.scene");
        sceneModel.setLocalScale(2f);
        rootNode.attachChild(sceneModel);
    }

    private void configuraTeclado() {
        //mapeia as teclas com alguma acao
        inputManager.addMapping("Cor", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Up", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Down", new KeyTrigger(KeyInput.KEY_S));
        //ActionListener verifica se uma tecla foi pressionada ou se foi solta        
        inputManager.addListener(actionListener, "Cor");
        //AnalogListener verifica quanto tempo a tecla esta sendo pressinada
        inputManager.addListener(analogListener, "Left", "Right", "Up", "Down");
        //exibe o ponteiro do mouse na tela
        inputManager.setCursorVisible(true);
    }

    @Override
    public void simpleInitApp() {
        //cor do fundo/horizonte  
        viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));

        //luz ambiente
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(1.3f));
        rootNode.addLight(al);

        //luz direcional
        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal());
        rootNode.addLight(dl);

        // Desabilita as informações de FPS e Debug na tela
        this.setDisplayFps(false);
        this.setDisplayStatView(false);

        //posicao da camera
        myCam = cam;
        myCam.setFrustumPerspective(45f, (float) cam.getWidth() / cam.getHeight(), 0.01f, 1000f);
        myCam.setLocation(new Vector3f(0, 3, 0));

        //o usuário precisa manter o botão esquerdo do mouse pressionado na cena para rotacionar a camera
        flyCam.setDragToRotate(true);

        //nao pausa o jogo quando a janela estiver fora de foco
        setPauseOnLostFocus(false);

        configuraTeclado();
        adicionaCenario();
        //adicionaJogador("Blue Cube", new Vector3f(0, 2, -2), ColorRGBA.Blue);

        Serializer.registerClass(HelloMessage.class);
        Serializer.registerClass(PlayerConnectedMessage.class);
        Serializer.registerClass(ColorMessage.class);
        Serializer.registerClass(MoveMessage.class);

        //conexao com o servidor
        try {
            myClient = Network.connectToServer(Globals.NAME, Globals.VERSION, Globals.DEFAULT_SERVER, Globals.DEFAULT_PORT);
            myClient.start();
        } catch (IOException ex) {
        }

        //adicao dos Listeners        
        myClient.addClientStateListener(this);
        myClient.addMessageListener(new ClientListener(this),
                PlayerConnectedMessage.class,
                HelloMessage.class,
                ColorMessage.class,
                MoveMessage.class);

        Random r = new Random();
        PlayerConnectedMessage pcm = new PlayerConnectedMessage(this.ClientID,
                ColorRGBA.randomColor(),
                new Vector3f(r.nextInt(2), r.nextInt(2), -2));
        myClient.send(pcm.setReliable(true));
    }

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean keyPressed, float tpf) {
            if (name.equals("Cor") && keyPressed) {
                colorMessage.setClientId(ClientID);
                colorMessage.setColor(ColorRGBA.randomColor());
                myClient.send(colorMessage);
            }
        }
    };

    private final AnalogListener analogListener = new AnalogListener() {
        @Override
        public void onAnalog(String name, float value, float tpf) {
            movMessage.setClientId(ClientID);

            if (name.equals("Right")) {
                movMessage.setPositon(value * velocidade, 0, 0);
                myClient.send(movMessage.setReliable(false));
            } else if (name.equals("Left")) {
                movMessage.setPositon(-value * velocidade, 0, 0);
                myClient.send(movMessage.setReliable(false));
            } else if (name.equals("Up")) {
                movMessage.setPositon(0, 0, -value * velocidade);
                myClient.send(movMessage.setReliable(false));
            } else if (name.equals("Down")) {
                movMessage.setPositon(0, 0, value * velocidade);
                myClient.send(movMessage.setReliable(false));
            }
        }
    };

    @Override
    public void clientConnected(Client client) {
        System.out.println("Cliente #" + client.getId() + " está pronto.");
    }

    @Override
    public void clientDisconnected(Client client, ClientStateListener.DisconnectInfo info) {
        System.out.println("Cliente #" + client.getId() + " saiu.");
    }

    @Override
    public void destroy() {
        try {
            myClient.close();
        } catch (Exception ex) {
        }
        super.destroy();
    }

}
