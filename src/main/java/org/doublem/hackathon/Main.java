package org.doublem.hackathon;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.doublem.hackathon.data.Sector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import static org.opencv.imgproc.Imgproc.cvtColor;

/**
 * Created by mmatosevic on 17.2.2015.
 */
public class Main {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private static Image outputImage;
    private static JLabel lbl = new JLabel();
    private static JFrame jFrame=new JFrame();

    static String NAME = Main.class.getCanonicalName();
    static int IMG_WIDTH = 800;
    static int IMG_HEIGHT = 600;
    private static final int SECTORS_W = 10;
    private static final int SECTORS_H = 8;
    private static Sector[][] sectors;

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println(NAME + " OK");
        System.out.println("OpenCV version: " + Core.VERSION);
        Collection<Sector> sectorCollection = new ArrayList<Sector>(SECTORS_W*SECTORS_H);

        LinkedList<Mat> buffer = new LinkedList<Mat>();

        VideoCapture capture = new VideoCapture();
        Mat frame = new Mat(IMG_HEIGHT, IMG_WIDTH, CvType.CV_8UC3, new Scalar(0));

        capture.open(0);
        if(!capture.retrieve(frame)){
            System.out.println("Unable to retrieve!");
            System.exit(1);
        }

        int width = frame.width();
        int height = frame.height();
        final int SECTOR_W = (int)Math.floor((double)width / SECTORS_W);
        final int SECTOR_H = (int)Math.floor((double)height / SECTORS_H);
        sectors = new Sector[SECTORS_H][SECTORS_W];

        HttpServer server = startServer(3344);

        for(int y=0; y<SECTORS_H; y++){
            for(int x=0; x<SECTORS_W; x++){
                int correctedWith = SECTOR_W;
                int correctedHeight = SECTOR_H;
                if((x * SECTOR_W) + SECTOR_W > IMG_WIDTH){
                    correctedWith = IMG_WIDTH - ((x * SECTOR_W) + SECTOR_W);
                }
                if((y * SECTOR_H) + SECTOR_H > IMG_HEIGHT){
                    correctedHeight = IMG_HEIGHT - ((y * SECTOR_H) + SECTOR_H);
                }
                Sector sector = createSector(x * SECTOR_W+1, y * SECTOR_H+1, correctedWith-1, correctedHeight-1);
                sectorCollection.add(sector);
                sectors[y][x] = sector;
            }
        }

        Boolean status;
        outputImage = convert(frame);
        showImage();
        while(true){
            if(!capture.grab()){
                System.out.println("Unable to grab frame!");
            }
            status = capture.retrieve(frame);

            if(status){
                BufferedImage image;
                Mat gsc = new Mat(IMG_HEIGHT, IMG_WIDTH, CvType.CV_8UC1);
                cvtColor(frame, gsc, Imgproc.COLOR_RGB2GRAY);
                buffer.add(gsc);
                while(buffer.size() > 3){
                    buffer.removeFirst();
                }
                if(buffer.size() == 3){
                    Mat prev = buffer.get(0);
                    Mat current = buffer.get(1);
                    Mat next = buffer.get(2);

                    Mat d1 = new Mat(IMG_HEIGHT, IMG_WIDTH, CvType.CV_8UC1, new Scalar(125));
                    Core.absdiff(prev, next, d1);

                    Mat d2 = new Mat(IMG_HEIGHT, IMG_WIDTH, CvType.CV_8UC1, new Scalar(148));
                    Core.absdiff(next, current, d2);
                    Mat result = new Mat(IMG_HEIGHT, IMG_WIDTH, CvType.CV_8UC1, new Scalar(234));
                    Core.bitwise_and(d1, d2, result);
                    Imgproc.threshold(result, result, 25, 255, Imgproc.THRESH_BINARY);

                    java.util.List<MatOfPoint> contours = new ArrayList<MatOfPoint>(10);
                   // Imgproc.findContours(result, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_TC89_KCOS);
                    int active = 0;
                    for(Sector s : sectorCollection){
                        s.analyzeData(result);
                        if(s.isActive()){
                            active++;
                        }
                    }
                    System.out.println("Active sectors: " + active);

                    if(contours.size() > 0){
                        System.out.println("Found contours: " + contours.size());
                    }
                    Mat dst = new Mat(IMG_HEIGHT, IMG_WIDTH, CvType.CV_8UC3, new Scalar(0));
                    for(MatOfPoint point : contours){
                        Rect rect = Imgproc.boundingRect(point);

                        Core.rectangle(dst, new Point(rect.x,rect.y), new Point(rect.x+rect.width,rect.y+rect.height), new Scalar(0,255,0));
                    }


                    image = convert(result);
                }else{
                    image = convert(frame);
                }


                outputImage = image;
                lbl.setIcon(new ImageIcon(Main.outputImage));
                jFrame.validate();
                //Thread.sleep(1000);
            }else{
                System.out.println("Can't fetch image!");
            }

        }


    }

    private static HttpServer startServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        HttpContext dataContext = server.createContext("/data", new DataHandler());
        server.setExecutor(null);
        server.start();
        return server;
    }


    private static Sector createSector(int x, int y, int w, int h){
        Rect regionOfInterest = new Rect(x, y, w, h);
        return new Sector(regionOfInterest, 50, 10, 2000);
    }

    private static BufferedImage convert(Mat m){
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if ( m.channels() > 1 ) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = m.channels()*m.cols()*m.rows();
        byte [] b = new byte[bufferSize];
        m.get(0,0,b); // get all the pixels
        BufferedImage image = new BufferedImage(m.cols(),m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;

    }

    private static void showImage(){
        ImageIcon icon=new ImageIcon(Main.outputImage);
        jFrame.setLayout(new FlowLayout());
        jFrame.setSize(outputImage.getWidth(null) + 50, outputImage.getHeight(null) + 50);
        lbl.setIcon(icon);
        jFrame.add(lbl);
        jFrame.setVisible(true);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    static class DataHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {

            JSONObject response = new JSONObject();
            JSONArray rows = new JSONArray();
            for(int y = 0; y<SECTORS_H; y++){
                JSONArray row = new JSONArray();
                for(int x = 0; x<SECTORS_W; x++){
                    Sector s = sectors[y][x];
                    JSONObject sector = new JSONObject();
                    sector.put("activity", s.isActive());
                    row.add(sector);
                }
                rows.add(row);
            }
            response.put("sectors", rows);

            String jsonString = response.toJSONString();
            httpExchange.sendResponseHeaders(200, jsonString.length());
            OutputStream responseBody = httpExchange.getResponseBody();
            responseBody.write(jsonString.getBytes());
            responseBody.close();
        }
    }
}
