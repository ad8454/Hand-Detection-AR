/***
 * @author: Ajinkya Dhaigude
 */

package com.example.ajinkya.cvisionapp;

import android.app.ActionBar;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.opengl.EGLConfig;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.opengl.GLSurfaceView.Renderer;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.*;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.utils.Converters;
import org.opencv.video.Video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {

    private JavaCameraView javaCameraView;
    private Mat ycc;
    private int camDim[] = {320, 240};          // could be better
    private float offsetFactX, offsetFactY;
    private float scaleFactX, scaleFactY;
    private boolean handDetected = false;
    private Scalar handColor;
    private Scalar minHSV;
    private Scalar maxHSV;
    private Mat frame, frame2;
    private Point palmCenter;
    private List<Point> fingers;
    private TermCriteria termCriteria;
    private List<Rect> allRoi;
    private List<Mat> allRoiHist;
    private MatOfFloat ranges;
    private MatOfInt channels;
    private Mat dstBackProject;
    private MatOfPoint palmContour;
    private MatOfPoint hullPoints;
    private MatOfInt hull;
    private Mat hierarchy;
    private Mat touchedMat;
    private MatOfInt4 convexityDefects;
    private Mat nonZero;
    private Mat nonZeroRow;
    private List<MatOfPoint> contours;
    private GLRenderer myGLRenderer;
    private int speedTime = 0;
    private int speedFingers = 0;


    static {
        if (!OpenCVLoader.initDebug())
            Log.e("init", "noo");
        else
            Log.e("init", "yess");
    }

    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("START!!!", "OpenCV loaded successfully");
                    javaCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        setContentView(R.layout.activity_main);

        javaCameraView = (JavaCameraView) findViewById(R.id.java_surface_view);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);

        javaCameraView.setMaxFrameSize(camDim[0], camDim[1]);


        GLSurfaceView myGLView = new GLSurfaceView(this);
        myGLView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        myGLView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        myGLRenderer = new GLRenderer();
        myGLView.setRenderer(myGLRenderer);
        addContentView(myGLView, new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT));
        myGLView.setZOrderMediaOverlay(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onResume(){
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_10, this, baseLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null)
            javaCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        setScaleFactors(width, height);
        myGLRenderer.setVidDim(camDim[0], camDim[1]);
        ycc = new Mat(height, width, CvType.CV_8UC3);
        handColor = new Scalar(255);
        minHSV = new Scalar(3);
        maxHSV = new Scalar(3);
        frame = new Mat();
        termCriteria = new TermCriteria(TermCriteria.COUNT | TermCriteria.EPS, 10, 1);
        allRoi = new ArrayList<>();
        allRoiHist = new ArrayList<>();
        ranges = new MatOfFloat(0, 180);
        channels = new MatOfInt(0);
        dstBackProject = new Mat();
        palmContour = new MatOfPoint();
        hullPoints = new MatOfPoint();
        hull = new MatOfInt();
        hierarchy  = new Mat();
        touchedMat = new Mat();
        convexityDefects = new MatOfInt4();
        nonZero = new Mat();
        frame2 = new Mat();
        nonZeroRow = new Mat();
        contours = new ArrayList<>();
        palmCenter = new Point(-1, -1);

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        ycc = inputFrame.rgba();
        //Imgproc.GaussianBlur(ycc, ycc, new Size(9, 9), 0);
//        Imgproc.threshold(ycc, ycc, 70, 255, Imgproc.THRESH_BINARY_INV+Imgproc.THRESH_OTSU);
        //
        if (handDetected) {

            //return frame;

            frame = ycc.clone();
//            for(int i=0; i<allRoi.size(); i++){
//                Rect roi = motionTrack(frame, allRoi.get(i), allRoiHist.get(i)); // change to frame
//                allRoi.set(i, roi);
//                Imgproc.rectangle(ycc, roi.tl(), roi.br(), new Scalar(255, 0, 255), 3);
//            }
//

            Imgproc.GaussianBlur(frame, frame, new Size(9, 9), 5);
            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2HSV_FULL);
            Core.inRange(frame, minHSV, maxHSV, frame);

//            Point palm = getDistanceTransformCenter(frame);
//            myGLRenderer.setPos(palm.x, palm.y);
//
//
//            // new: maybe move to function
//            frame = ycc.clone();
//            double avgHSV[] = getAvgHSV(frame, (int)palm.x, (int)palm.y, frame.rows(), frame.cols());
//            assignHSV(avgHSV);


            //TODO: check if needs to be released
            contours =  getAllContours(frame);
            int indexOfPalmContour = getPalmContour(contours);
            if(indexOfPalmContour < 0)
                myGLRenderer.setRenderCube(false);
            else{

                Point palm = getDistanceTransformCenter(frame);
//                Imgproc.circle(ycc, palm, 6, new Scalar(25, 120, 255));
                myGLRenderer.setPos(palm.x, palm.y);
                Rect roi = Imgproc.boundingRect(contours.get(indexOfPalmContour));
                myGLRenderer.setCubeSize(getEuclDistance(palm, roi.tl()));

//                palmContour = contours.get(indexOfPalmContour);
//                List<Point> listHullPoints = getConvexHullPoints(palmContour);
//                //Log.e("hull size ------>", hullPoints.size()+"");
//                contours.clear();
//                Point hullArray[] = new Point[listHullPoints.size()];
//                listHullPoints.toArray(hullArray);
//                hullPoints = new MatOfPoint(hullArray);
//                contours.add(hullPoints);
//                contours.add(palmContour);
//                // draw convex hull
//                Imgproc.drawContours(ycc, contours, 0, new Scalar(0, 255, 0));
//                Imgproc.drawContours(ycc, contours, 1, new Scalar(0, 0, 255));
//
//
//
//                // new: maybe move to function
////                frame = ycc.clone();
////                getAvgHSV(frame);
//
//
//                Imgproc.rectangle(ycc, roi.tl(), roi.br(), new Scalar(0, 0, 255), 1);
//                Log.e("mop", contours.get(indexOfPalmContour).dims() + "    " + contours.get(0).get(0, 0).length);
//
//                Imgproc.convexityDefects(palmContour, hull, convexityDefects);
//                List<Integer> defectIndices = getDefects(convexityDefects.toList());
//                Point palmContourPoints[] = palmContour.toArray();
////                Log.e("defects -->", defectIndices.size()+"");
//
//                List<Point> defectPoints = new ArrayList<>();
//                for(int i=0; i<defectIndices.size(); i+=4) {
//                    Imgproc.circle(ycc, palmContourPoints[defectIndices.get(i + 2)], 6, new Scalar(255, 105, 185));
//                    defectPoints.add(palmContourPoints[defectIndices.get(i + 2)]);
//                }
//                Point defectArray[] = new Point[defectPoints.size()];
//                defectPoints.toArray(defectArray);
//                hullPoints = new MatOfPoint(defectArray);
//                contours.add(0, hullPoints);
//                Imgproc.drawContours(ycc, contours, 0, new Scalar(254, 10, 0));



                List<Point> hullPoints = getConvexHullPoints(contours.get(indexOfPalmContour)); //1   //indexOfPalmContour
                fingers = getFingersTips(hullPoints, frame.rows());
                Collections.reverse(fingers);

                int fSize = fingers.size();
                if(fSize != speedFingers){
                    speedFingers = fSize;
                    speedTime = 0;
                }
                else if(fSize != 5)
                    speedTime++;
                if(speedTime > 8)
                    myGLRenderer.setCubeRotation(fSize);
                Log.e("speed", speedTime+"  "+fSize);

//                for(int i=0; i<fingers.size(); i++) {
////                    Log.e("in f", getEuclDistance(fingers.get(i), palmCenter)+"");
//                    Imgproc.circle(ycc, fingers.get(i), 6, new Scalar(255, 0, 0));
//                    Imgproc.putText(ycc, fingers.get(i).x+" "+fingers.get(i).y, fingers.get(i), 1, 0.7, new Scalar(25, 0, 250) );
//                }

            }





            // convex defects and moment mass
//            Imgproc.convexityDefects(maxContour, hull, convexityDefects);
//            List<Integer> defectIndices = convexityDefects.toList();
//
//            Moments mont = Imgproc.moments(contours.get(0));
//            int x = (int) (mont.get_m10() / mont.get_m00());
//            int y = (int) (mont.get_m01() / mont.get_m00());
//            Point palm = new Point(x, y);
//            Imgproc.circle(ycc, palm, 6, new Scalar(25, 120, 255));



//




            //ycc = getDistanceTransformCenter(ycc);


//                contours.add(new MatOfPoint());
//                contours.get(contours.size()-1).fromList(hullPoints);
//                Imgproc.drawContours(frame, tempContours, tempIndex, new Scalar(0, 255, 0));
//
//                // Bounded Rectangle
//                Rect roi = Imgproc.boundingRect(contours.get(indexOfMaxContour));
//                Imgproc.rectangle(frame, roi.tl(), roi.br(), new Scalar(0, 0, 255), 3);
//                Log.e("mop", contours.get(0).dims() + "    " + contours.get(0).get(0, 0).length);
//
//                 //   Rotated Rectangle
//                RotatedRect box = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(indexOfMaxContour).toArray()));
//                Point corners[] = new Point[4];
//                box.points(corners);
//                for(int i=0; i<4; ++i) {
//                    Imgproc.line(frame, corners[i], corners[(i + 1) % 4], new Scalar(255, 0, 0));
//               }
//            return frame;
//            }
            return ycc;
        }
        return ycc;
    }

    @Override
    public void onCameraViewStopped() {
        frame.release();
        ycc.release();
        ranges.release();
        channels.release();
        dstBackProject.release();
        palmContour.release();
        hullPoints.release();
        hull.release();
        hierarchy.release();
        touchedMat.release();
        convexityDefects.release();
        nonZero.release();
        frame2.release();
        nonZeroRow.release();
        while (allRoiHist.size() > 0)
            allRoiHist.get(0).release();
        while (contours.size() > 0)
            contours.get(0).release();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(! handDetected){

            // clone and blur touched frame
            frame = ycc.clone();
            Imgproc.GaussianBlur(frame, frame, new Size(9, 9), 5);

            // calc x, y coords coz resolution is scaled on device display
            int x = Math.round((event.getX() - offsetFactX) * scaleFactX) ;
            int y = Math.round((event.getY() - offsetFactY) * scaleFactY);

            int rows = frame.rows();
            int cols = frame.cols();

            if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

            palmCenter.x = x;
            palmCenter.y = y;

//            int xx = (int)((0 / scaleFactX)+ offsetFactX);
//            int yy = (int)((0 / scaleFactY)+ offsetFactY);
//
//            Log.e("1st", xx+"  "+yy);
//
//            xx = (int)((320 / scaleFactX)+ offsetFactX);
//            yy = (int)((240 / scaleFactY)+ offsetFactY);
//
//            Log.e("2nd", xx+"  "+yy);

            // get average HSV values of a square patch around the touched pixel
            // and store them in global variables

            getAvgHSV(frame);


            // do below stuff in real time


//            // to get palm center: do better coz image patch was in HSV earlier
              // Needs work. Maybe find mean of all points
//            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2HSV_FULL);
//            Core.inRange(frame, minHSV, maxHSV, frame);
//
//
//
//////            basePoint = getDistanceTransformCenter(ycc);
////            basePoint = new Point(x, y);
////
//            List<MatOfPoint> contours =  getAllContours(frame);
//            int indexOfMaxContour = getIndexOfMaxContour(contours);
//            List<Point> hullPoints = getConvexHullPoints(contours.get(indexOfMaxContour));
//            fingers = getFingersTips(hullPoints, rows);
//            Collections.reverse(fingers);   // thumb is 0
//
//
//
//            //************test***********
////            //Imgproc.drawContours(frame, contours, indexOfMaxContour, new Scalar(255, 0, 0));
////            frame = ycc.clone();
////            Point hullArray[] = new Point[hullPoints.size()];
////            hullPoints.toArray(hullArray);
////            contours.add(new MatOfPoint(hullArray));
////            //contours.get(contours.size()-1).fromList(hullPoints);
////            Imgproc.drawContours(frame, contours, contours.size()-1, new Scalar(0, 255, 0));
////
////
//
//
//
//
//            for(int i=0; i<fingers.size(); i++){
//                allRoi.add(new Rect());
//                allRoiHist.add(new Mat());
//                assignRoiHist(fingers.get(i), ycc, allRoi.get(i), allRoiHist.get(i));
//            }


            handDetected = true;
        }
        return false;
    }

    protected List<Integer> getDefects(List<Integer> defectIndicesOld){
        int thresh = 800;
        int prevDepth = 0;
        List<Integer> defectIndices = new ArrayList<Integer>();
        for(int i = 0; i<defectIndicesOld.size(); i+=4) {
            int curDepth = defectIndicesOld.get(i+3);
//            Log.e("depth", i+"  "+curDepth);
//            if (curDepth < prevDepth)
//                defectIndices.addAll(defectIndicesOld.subList(i-4, i));
//            prevDepth = curDepth;
            if(curDepth > thresh)
                defectIndices.addAll(defectIndicesOld.subList(i, i+4));
        }
        return defectIndices;
    }

    protected Rect motionTrack(Mat frame, Rect roi, Mat roiHist){
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2HSV_FULL);

        List<Mat> tempList = new ArrayList<>();
        tempList.add(frame);
        Imgproc.calcBackProject(tempList, channels, allRoiHist.get(0), dstBackProject, ranges, 1);

        Video.meanShift(dstBackProject, roi, termCriteria);

        return roi;
    }

    protected void assignRoiHist(Point point, Mat frame, Rect roi, Mat roiHist){
        int halfSide = 10;
        roi.x = ((int) point.x - halfSide > 0)? (int) point.x - halfSide : 0;
        roi.y = ((int) point.y - halfSide > 0)? (int) point.y - halfSide : 0;
        roi.width = (2 * halfSide < frame.width())? 2 * halfSide : frame.width();
        roi.height = (2 * halfSide < frame.height())? 2 * halfSide : frame.height();

        Log.e("roi", roi.x+" "+roi.y+" "+roi.width+" "+roi.height);

        Mat submat = frame.submat(roi);
        Mat mask = new Mat();
        MatOfInt histSize = new MatOfInt(180);

        Imgproc.cvtColor(submat, submat, Imgproc.COLOR_RGB2HSV_FULL);
        Core.inRange(submat, minHSV, maxHSV, mask);
        List<Mat> tempMatList = new ArrayList();
        tempMatList.add(submat);
        Imgproc.calcHist(tempMatList, channels, mask, roiHist, histSize, ranges);
        Core.normalize(roiHist, roiHist, 0, 255, Core.NORM_MINMAX);

        submat.release();
        mask.release();
        histSize.release();
    }

    protected Point getDistanceTransformCenter(Mat frame){

        Imgproc.distanceTransform(frame, frame, Imgproc.CV_DIST_L2, 3);
        frame.convertTo(frame, CvType.CV_8UC1);
        Core.normalize(frame, frame, 0, 255, Core.NORM_MINMAX);
        Imgproc.threshold(frame, frame, 254, 255, Imgproc.THRESH_TOZERO);
        Core.findNonZero(frame, nonZero);

        // are you kidding me
        int sumx = 0, sumy = 0;
        for(int i=0; i<nonZero.rows(); i++) {
            sumx += nonZero.get(i, 0)[0];
            sumy += nonZero.get(i, 0)[1];
        }
        sumx /= nonZero.rows();
        sumy /= nonZero.rows();

        return new Point(sumx, sumy);
    }

    protected List<Point> getFingersTips(List<Point> hullPoints, int rows){
        // group into clusters and find distance between each cluster. distance should approx be same
        double betwFingersThresh = 80;
        double distFromCenterThresh = 80;
        double thresh = 80;
        List<Point> fingerTips  = new ArrayList<>();
        for(int i=0; i<hullPoints.size(); i++){
            Point point = hullPoints.get(i);
            if(rows - point.y < thresh){ //betwFingersThresh     // lies very near frame edge hence arm
                   // || getEuclDistance(point, palmCenter) < distFromCenterThresh) {
//                Log.e("dist", getEuclDistance(point, palmCenter)+"");
                continue;
            }
            if(fingerTips.size() == 0){
                fingerTips.add(point);
                continue;
            }
            Point prev = fingerTips.get(fingerTips.size() - 1);
            double euclDist = getEuclDistance(prev, point);
            if(getEuclDistance(prev, point) > thresh/2 &&
                    getEuclDistance(palmCenter, point) > thresh) {
//                Log.e("be f", euclDist+"");
                fingerTips.add(point);
            }
            if(fingerTips.size() == 5)  // prevent detection of point after thumb
                break;
        }
        return fingerTips;
    }

    protected double getEuclDistance(Point one, Point two){
        return Math.sqrt(Math.pow((two.x - one.x), 2)
                + Math.pow((two.y - one.y), 2));
    }

    protected List<Point> getConvexHullPoints(MatOfPoint contour){
        Imgproc.convexHull(contour, hull);
        List<Point> hullPoints = new ArrayList<>();
        for(int j=0; j < hull.toList().size(); j++){
            hullPoints.add(contour.toList().get(hull.toList().get(j)));
        }
        return hullPoints;
    }

    protected int getPalmContour(List<MatOfPoint> contours){

        Rect roi;
        int indexOfMaxContour = -1;
//        int currentMax = 0;
        for (int i = 0; i < contours.size(); i++) {
            roi = Imgproc.boundingRect(contours.get(i));
            if(roi.contains(palmCenter))
                return i;
//            if (contours.get(i).dims() > currentMax)
//                indexOfMaxContour = i;
        }
        return indexOfMaxContour;
    }

    protected List<MatOfPoint> getAllContours(Mat frame){
        frame2 = frame.clone();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(frame2, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        return contours;
    }

    protected void getAvgHSV(Mat frame){
        // consider square patch around touched pixel

        int x = (int) palmCenter.x;
        int y = (int) palmCenter.y;
        int rows = frame.rows();
        int cols = frame.cols();

        Rect touchedSquare = new Rect();
        int squareSide = 20;

        touchedSquare.x = (x > squareSide) ? x - squareSide : 0;
        touchedSquare.y = (y > squareSide) ? y - squareSide : 0;

        touchedSquare.width = (x + squareSide < cols) ?
                x + squareSide - touchedSquare.x : cols - touchedSquare.x;
        touchedSquare.height = (y + squareSide < rows) ?
                y + squareSide - touchedSquare.y : rows - touchedSquare.y;

        touchedMat = frame.submat(touchedSquare);

        // convert patch to HSV and get average values
        Imgproc.cvtColor(touchedMat, touchedMat, Imgproc.COLOR_RGB2HSV_FULL);

        Scalar sumHSV = Core.sumElems(touchedMat);
        int total = touchedSquare.width * touchedSquare.height;
        double avgHSV[] = {sumHSV.val[0] / total, sumHSV.val[1] / total, sumHSV.val[2] / total};
        assignHSV(avgHSV);
    }

    protected void assignHSV(double avgHSV[]){
        minHSV.val[0] = (avgHSV[0] > 10) ? avgHSV[0] - 10 : 0;
        maxHSV.val[0] = (avgHSV[0] < 245) ? avgHSV[0] + 10 : 255;

        minHSV.val[1] = (avgHSV[1] > 130) ? avgHSV[1] - 100 : 30;
        maxHSV.val[1] = (avgHSV[1] < 155) ? avgHSV[1] + 100 : 255;

        minHSV.val[2] = (avgHSV[2] > 130) ? avgHSV[2] - 100 : 30;
        maxHSV.val[2] = (avgHSV[2] < 155) ? avgHSV[2] + 100 : 255;

        Log.e("HSV", avgHSV[0]+", "+avgHSV[1]+", "+avgHSV[2]);
        Log.e("HSV", minHSV.val[0]+", "+minHSV.val[1]+", "+minHSV.val[2]);
        Log.e("HSV", maxHSV.val[0]+", "+maxHSV.val[1]+", "+maxHSV.val[2]);
    }

    protected Mat downSample(Mat ycc, int n){
        // TODO: erode then dilate

        for (int i=0; i<n; i++)
            Imgproc.pyrDown(ycc, ycc);
        return ycc;
    }

    protected void setScaleFactors(int vidWidth, int vidHeight){
        float deviceWidth = javaCameraView.getWidth();
        float deviceHeight = javaCameraView.getHeight();
        if(deviceHeight - vidHeight < deviceWidth - vidWidth){
            float temp = vidWidth * deviceHeight / vidHeight;
            offsetFactY = 0;
            offsetFactX = (deviceWidth - temp) / 2;
            scaleFactY = vidHeight / deviceHeight;
            scaleFactX = vidWidth / temp;
        }
        else{
            float temp = vidHeight * deviceWidth / vidWidth;
            offsetFactX= 0;
            offsetFactY = (deviceHeight - temp) / 2;
            scaleFactX = vidWidth / deviceWidth;
            scaleFactY = vidHeight / temp;
        }
    }
}

