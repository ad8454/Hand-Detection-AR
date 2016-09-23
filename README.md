# Hand-Detection-AR
This is an Augmented Reality Android app. It detects and tracks the user's hand in real time and augments a rotating cube on top of it.
The rotation speed can be set by the user by holding up the corresponding number of fingers.

**Watch video <a href="https://www.youtube.com/watch?v=zdT33t92WN0&feature=youtu.be"> here</a>.**


<img src="https://github.com/ad8454/Hand-Detection-AR/blob/master/ar_stage.JPG" width="600">

The methodology, as illustrated above, is:
* Take user input and detect hand color in HSV
* Segment hand and compute convex hull
* Use convex hull to detect fingertips
* Use distance transform to estimate palm center
* Render cube at palm center
* Set cube rotation speed with finger recognition
* Estimate depth with contour size and set cube scale


**Screenshot of final app in action:**

<img src="https://github.com/ad8454/Hand-Detection-AR/blob/master/ar_final.JPG" width="800">
