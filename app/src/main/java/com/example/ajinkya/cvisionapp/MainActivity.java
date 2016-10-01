/**
 * The main and only activity in the app. The class opens
 * up a camera view in landscape mode of the dimensions 
 * specified. Upon user input, it computes the anchor point
 * for augmenting the cube and renders it.
 *
 *
 * @author  Ajinkya Dhaigude
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

	// declare global variables.
	// Reuse OpenCV instances like Mat because creating new objects is expensive
    private JavaCameraView javaCameraView;
    private Mat ycc;
    private int camDim[] = {320, 240};          // TODO: higher resolution
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


	// Initial check for OpenCV
    static {
        if (!OpenCVLoader.initDebug())
            Log.e("init", "OpenCV NOT loaded");
        else
            Log.e("init", "OpenCV successfully loaded");
    }

	// anonymous class for initializing loader callback
    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("Start", "OpenCV callback successful");
                    javaCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

	
	/**
	 * Called only once on app start up
	 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);			// prevent screen from going to sleep
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        setContentView(R.layout.activity_main);

        javaCameraView = (JavaCameraView) findViewById(R.id.java_surface_view);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);

        javaCameraView.setMaxFrameSize(camDim[0], camDim[1]);


		// set up OpenGL view
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
		// initialize global variables on camera start
		
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
		// called for every frame on video feed
		
        ycc = inputFrame.rgba();
        if (handDetected) {
			// clone frame beacuse original frame needed for display
            frame = ycc.clone();

			// remove noise and convert to binary in HSV range determined by user input
            Imgproc.GaussianBlur(frame, frame, new Size(9, 9), 5);
            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2HSV_FULL);
            Core.inRange(frame, minHSV, maxHSV, frame);

//            Point palm = getDistanceTransformCenter(frame);

			// get all possible contours and then determine palm contour
            contours =  getAllContours(frame);
            int indexOfPalmContour = getPalmContour(contours);
			
            if(indexOfPalmContour < 0)
                myGLRenderer.setRenderCube(false);		// no palm in frame
            else{
				// get anchor point for cube rendering
                Point palm = getDistanceTransformCenter(frame);
                myGLRenderer.setPos(palm.x, palm.y);
                Rect roi = Imgproc.boundingRect(contours.get(indexOfPalmContour));
				
				// set cube scale
                myGLRenderer.setCubeSize(getEuclDistance(palm, roi.tl()));

				// get finger tips for gesture recognition
                List<Point> hullPoints = getConvexHullPoints(contours.get(indexOfPalmContour));
                fingers = getFingersTips(hullPoints, frame.rows());
                Collections.reverse(fingers);

				// set cube rotation speed
                int fSize = fingers.size();
                if(fSize != speedFingers){
                    speedFingers = fSize;
                    speedTime = 0;
                }
                else if(fSize != 5)
                    speedTime++;
                if(speedTime > 8)
                    myGLRenderer.setCubeRotation(fSize);
            }
            return ycc;
        }
        return ycc;
    }

    @Override
    public void onCameraViewStopped() {
		// release all resources on camera close
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

            // calculate x, y coords because resolution is scaled on device display
            int x = Math.round((event.getX() - offsetFactX) * scaleFactX) ;
            int y = Math.round((event.getY() - offsetFactY) * scaleFactY);

            int rows = frame.rows();
            int cols = frame.cols();

			// reurn if touched point is outside camera resolution
            if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

			// set palm center point and average HSV value
            palmCenter.x = x;
            palmCenter.y = y;

            getAvgHSV(frame);

            handDetected = true;
        }
        return false;
    }

	/**
	 * Method to get convex hull defects.
	 */
    protected List<Integer> getDefects(List<Integer> defectIndicesOld){
        int thresh = 800;
        int prevDepth = 0;
        List<Integer> defectIndices = new ArrayList<Integer>();
        for(int i = 0; i<defectIndicesOld.size(); i+=4) {
            int curDepth = defectIndicesOld.get(i+3);
            if(curDepth > thresh)
                defectIndices.addAll(defectIndicesOld.subList(i, i+4));
        }
        return defectIndices;
    }

	
	/**
	 * Method that uses mean shift algorithm to track each finger tip.
	 * Not used beacuse not robust.
	 */
    protected Rect motionTrack(Mat frame, Rect roi, Mat roiHist){
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2HSV_FULL);

        List<Mat> tempList = new ArrayList<>();
        tempList.add(frame);
        Imgproc.calcBackProject(tempList, channels, allRoiHist.get(0), dstBackProject, ranges, 1);

        Video.meanShift(dstBackProject, roi, termCriteria);

        return roi;
    }

	
	/**
	 * Method to compute and assign histogram of given Region of Interest.
	 */
    protected void assignRoiHist(Point point, Mat frame, Rect roi, Mat roiHist){
        int halfSide = 10;
        roi.x = ((int) point.x - halfSide > 0)? (int) point.x - halfSide : 0;
        roi.y = ((int) point.y - halfSide > 0)? (int) point.y - halfSide : 0;
        roi.width = (2 * halfSide < frame.width())? 2 * halfSide : frame.width();
        roi.height = (2 * halfSide < frame.height())? 2 * halfSide : frame.height();

        //Log.e("roi", roi.x+" "+roi.y+" "+roi.width+" "+roi.height);

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

	
	/**
	 * Method to compute and return strongest point of distance transform.
	 * For a binary image with palm in white, strongest point will be the palm center.
	 */
    protected Point getDistanceTransformCenter(Mat frame){

        Imgproc.distanceTransform(frame, frame, Imgproc.CV_DIST_L2, 3);
        frame.convertTo(frame, CvType.CV_8UC1);
        Core.normalize(frame, frame, 0, 255, Core.NORM_MINMAX);
        Imgproc.threshold(frame, frame, 254, 255, Imgproc.THRESH_TOZERO);
        Core.findNonZero(frame, nonZero);

        // have to manually loop through matrix to calculate sums
        int sumx = 0, sumy = 0;
        for(int i=0; i<nonZero.rows(); i++) {
            sumx += nonZero.get(i, 0)[0];
            sumy += nonZero.get(i, 0)[1];
        }
        sumx /= nonZero.rows();
        sumy /= nonZero.rows();

        return new Point(sumx, sumy);
    }

	
	/**
	 * Method to get number of fingers being help up in palm image
	 */
    protected List<Point> getFingersTips(List<Point> hullPoints, int rows){
        // group into clusters and find distance between each cluster. distance should approx be same
        double betwFingersThresh = 80;
        double distFromCenterThresh = 80;
        double thresh = 80;
        List<Point> fingerTips  = new ArrayList<>();
        for(int i=0; i<hullPoints.size(); i++){
            Point point = hullPoints.get(i);
            if(rows - point.y < thresh)
                continue;
            if(fingerTips.size() == 0){
                fingerTips.add(point);
                continue;
            }
            Point prev = fingerTips.get(fingerTips.size() - 1);
            double euclDist = getEuclDistance(prev, point);
			
            if(getEuclDistance(prev, point) > thresh/2 &&
                    getEuclDistance(palmCenter, point) > thresh)
                fingerTips.add(point);
				
            if(fingerTips.size() == 5)  // prevent detection of point after thumb
                break;
        }
        return fingerTips;
    }

	
	/**
	 * Method to get eucledean distance between two points.
	 */
    protected double getEuclDistance(Point one, Point two){
        return Math.sqrt(Math.pow((two.x - one.x), 2)
                + Math.pow((two.y - one.y), 2));
    }

	
	/**
	 * Method to get convex hull points.
	 */
    protected List<Point> getConvexHullPoints(MatOfPoint contour){
        Imgproc.convexHull(contour, hull);
        List<Point> hullPoints = new ArrayList<>();
        for(int j=0; j < hull.toList().size(); j++){
            hullPoints.add(contour.toList().get(hull.toList().get(j)));
        }
        return hullPoints;
    }

	
	/**
	 * Method to get contour of palm. Computed by the 
	 * knowledge that palm center has to lie inside it.
	 */
    protected int getPalmContour(List<MatOfPoint> contours){

        Rect roi;
        int indexOfMaxContour = -1;
        for (int i = 0; i < contours.size(); i++) {
            roi = Imgproc.boundingRect(contours.get(i));
            if(roi.contains(palmCenter))
                return i;
        }
        return indexOfMaxContour;
    }

	
	/**
	 * Method to get all possible contours in binary image frame.
	 */
    protected List<MatOfPoint> getAllContours(Mat frame){
        frame2 = frame.clone();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(frame2, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        return contours;
    }

	
	/**
	 * Method to assign average HSV value of palm
	 */
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

	/**
	 * Method to assign range of HSV values of palm
	 */
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

	/**
	 * Method to set scale factors for coordinate translation
	 */
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

