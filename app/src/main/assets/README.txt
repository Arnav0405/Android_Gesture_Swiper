Application Processing

Multiple Pages requires navigation in the application. Which is based on Stack.
Onboarding Page:
Get Started -> Button that switches to MainPage

MainPage:

This is the page that starts Instagram app on the mobile phone.
This page also initialises the models.
Steps to this page.

We need to add a button that starts the model and stops the models.
Tutorial Stage:
1. Initialise landmarker, and show LiveStreaning results on the page as a composable, no need to show camera, but info from camera is needed.

2. ONNX integration! Take input from the HandLandmarkerResults as ONNX-Input and run my model, show ONNX-Model output on the page as a composable.

3. Shift above code to a new page called TestPage, add button on the Onboarding Page to take to TestPage or MainPage.

3.5. TestPage opens phone's home page. 

4. Take output of model as android touch inputs, and test if they work on the home page as expected.

5. Now work on Instagram integration. Start Instagram and shift to reels section, either by manual touch or automatically.
