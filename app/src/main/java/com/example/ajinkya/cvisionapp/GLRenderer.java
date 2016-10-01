/***
 * Modification to the OpenGL sample file.
 *
 * @author Ajinkya Dhaigude
 */
 
package com.example.ajinkya.cvisionapp;

import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.util.Log;

import javax.microedition.khronos.opengles.GL10;


public class GLRenderer implements GLSurfaceView.Renderer {
	
	// global variables for cube parameters
    private Cube mCube = new Cube();
    private float mCubeRotation;

    private GL10 gl;

    private boolean renderCube = false;
    private float XScale = 0;
    private float YScale = 0;
    private float posX = 0;
    private float posY = 0;
    private int vidWidth = 0;
    private int vidHeight = 0;
    private float cubeSize = 1.0f;
    private double initSizeLength = -1;
    private double rotSpeed = 0.250f;

	
	/**
	 * Method to set cube rotation speed
	 */
    public void setCubeRotation(int val){
        rotSpeed = 0.250f * val*2;
        Log.e("Cube speed", rotSpeed+"");
    }

	
	/**
	 * Method to set cube scale size
	 */
    public void setCubeSize(double sizeLength){
        if(initSizeLength < 0)
            initSizeLength = sizeLength;
        cubeSize = (float) ((sizeLength/initSizeLength));
    }

	
	/**
	 * Method to set cube visibility
	 */
    public void setRenderCube(boolean val){
        renderCube = val;
    }

	
	/**
	 * Method to set frame dimensions
	 */
    public void setVidDim(int w, int h){
        vidWidth = w;
        vidHeight = h;
        XScale = 11.0f * 2 / vidWidth;
        YScale = 8.0f * 2 / vidHeight;

    }

	
	/**
	 * Method to set cube position
	 */
    public void setPos(double x, double y){
        x -= vidWidth/2;
        y -= vidHeight/2;
        posX = (float) x * XScale;
        posY = (float) y * -YScale;
        renderCube = true;
    }

	
	/**
	 * Method to render cube according to parameters
	 */
    public void onDrawFrame(GL10 gl) {
        if(renderCube) {
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
            gl.glLoadIdentity();


            gl.glTranslatef(posX, posY, -90.0f);


            gl.glRotatef(mCubeRotation, 1.0f, 1.0f, 1.0f); //up-down, left-right, cw-acw

            gl.glScalef(cubeSize, cubeSize, cubeSize);

            mCube.draw(gl);

            gl.glLoadIdentity();

            mCubeRotation -= rotSpeed;
        }
        else
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
    }

	
	/**
	 * Method called initially on surface creation
	 */
    @Override
    public void onSurfaceCreated(GL10 gl, javax.microedition.khronos.egl.EGLConfig config) {
        this.gl = gl;
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.5f);

        gl.glClearDepthf(1.0f);
        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glDepthFunc(GL10.GL_LEQUAL);

        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT,
                GL10.GL_NICEST);

    }

	
	/**
	 * Method called everytime surface changes.
	 * Ideally should never be called as surface should not change.
	 */
    public void onSurfaceChanged( GL10 gl, int width, int height ) {
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
		
        GLU.gluPerspective(gl, 10.0f, (float) width/ (float) height, 0.1f, 100.0f);

        gl.glViewport(0, 0, width, height);

        gl.glMatrixMode(GL10.GL_MODELVIEW);

        gl.glLoadIdentity();
    }
}
