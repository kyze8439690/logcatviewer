# LogcatViewer library

### Feature:

- Priority filter
- Clear logcat
- Export as file 
- Floating window

### Integrate guide

1. Clone this library as a project module, add module dependence.

2. Add launch code in your code:

    - Start logcat viewer
    ```kotlin
    LogcatActivity.start(getContext())
    ```
   
   - Start logcat viewer with log exclude rule
   ```kotlin
   val logcatExcludeRules = listOf(
       Pattern.compile(".*]: processMotionEvent MotionEvent \\{ action=ACTION_.*"),
       Pattern.compile(".*]: dispatchPointerEvent handled=true, event=MotionEvent \\{ action=ACTION_.*")
   )
   LogcatActivity.start(getContext(), logcatExcludeRules)
   ```

### Screenshot

<img src="https://raw.githubusercontent.com/kyze8439690/logcatviewer/master/screenshot/1.jpg" width="360">
<img src="https://raw.githubusercontent.com/kyze8439690/logcatviewer/master/screenshot/2.jpg" width="360">
<img src="https://raw.githubusercontent.com/kyze8439690/logcatviewer/master/screenshot/3.jpg" width="360">
<img src="https://raw.githubusercontent.com/kyze8439690/logcatviewer/master/screenshot/4.jpg" width="360">