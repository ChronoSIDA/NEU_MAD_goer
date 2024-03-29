package edu.neu.madcourse.mad_goer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.neu.madcourse.mad_goer.databinding.ActivityMainBinding;
import edu.neu.madcourse.mad_goer.messages.Event;
import edu.neu.madcourse.mad_goer.messages.EventType;
import edu.neu.madcourse.mad_goer.messages.User;

public class MainActivity extends AppCompatActivity{

    private NotificationManagerCompat notificationManagerCompat;
    private ActivityMainBinding binding;
    private static final String CURRENT_USER = "CURRENT_USER";
    private static String CURRENT_NAV = "0";
    private String timePattern = "yyyy-MM-dd HH:mm:ss z";
    private DateFormat df = new SimpleDateFormat(timePattern);

    private AlertDialog.Builder dialogBuilder;
    private AlertDialog dialog;
    private EditText newEventName;
    private Button newEventSave, newEventCancel;
    private Spinner newEventSpinner;
    private ArrayList<String> category_list = new ArrayList<>();
    public int currentMenuItemId = R.id.navigation_home;
    private String newEventType;
    private Boolean isLogin = false;
    private Boolean isCreated = false;

    // navigation bundles
    private BottomNavigationView navView;
    private AppBarConfiguration appBarConfiguration;
    private NavController navController;


    //saves all event from firebase
    //key is "eventID", value is Event
    private HashMap<String,Event> eventMap = new HashMap<>();

    private ArrayList<User> userList = new ArrayList<>();
    //this is user's personal eventmap, key is "eventID", value is "eventtype(host/attending/saved/past)"
    private Map<String,String> personalEventMap = new HashMap<>();
    private String currentUserName;
    public User currentUser;
    private ArrayList<ArrayList<Event>> listofEventLists = new ArrayList<>();
    private Boolean directFromSetting = false;
    private Boolean loginAlready = true;

    DatabaseReference databaseUserRef = FirebaseDatabase.getInstance().getReference("User");
    DatabaseReference databaseEventRef = FirebaseDatabase.getInstance().getReference("Event");


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        //need to be deleted
        //LoginActivity loginactivity = new LoginActivity();
        //currentUserName = loginactivity.getCurrentUserName();
        //currentUser = loginactivity.getCurrentUser();

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        currentUserName = extras.getString("nameTxt");

        DatabaseReference curUserRef = databaseUserRef.child(currentUserName);
        //read the user once from firebase, and save it to our user field.
        curUserRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentUser = snapshot.getValue(User.class);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                System.out.println("failed");
            }
        });

        notificationManagerCompat = NotificationManagerCompat.from(this);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel("myCh", "My Channel", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        String urlJson = "https://goerapp-4e3c7-default-rtdb.firebaseio.com/User/" + "" +".json";

        StringRequest request = new StringRequest(Request.Method.GET, urlJson, new Response.Listener<String>(){
            @Override
            public void onResponse(String s) {
                try {
                    JSONObject obj = new JSONObject(s);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        },new Response.ErrorListener(){
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                System.out.println("" + volleyError);
            }
        });

        RequestQueue rQueue = Volley.newRequestQueue(this);
        rQueue.add(request);


        navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_go, R.id.navigation_home, R.id.navigation_comment, R.id.navigation_setting)
                .build();

        navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);


        databaseEventRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Event newEvent = snapshot.getValue(Event.class);
                //if newevent !past
                    eventMap.put(newEvent.getEventID(),newEvent);
                //This is for sending notification when a new event is created
                //sendNotification(newEvent);
                if (!loginAlready) {
                    if(!currentUserName.equals(newEvent.getHost().getUserID())) {
                        if (currentUser.getInterestedTypeList().contains(newEvent.getCategory())) {
                            sendNotification(newEvent);
                        }
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                Event removedEvent = snapshot.getValue(Event.class);
                eventMap.remove(removedEvent.getEventID(),removedEvent);
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });


        // to determine if events are new or not
        new android.os.Handler(Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    public void run() {

                        loginAlready = false;
                    }
                },
                500);


        navView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener(){
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch(item.getItemId()){
                    case R.id.navigation_home:
                        currentMenuItemId = R.id.navigation_home;
                        navController.navigate(R.id.navigation_home);
                        return true;
                    case R.id.navigation_go:
                        currentMenuItemId = R.id.navigation_go;
                        navController.navigate(R.id.navigation_go);
                        return true;
                    case R.id.navigation_comment:
                        currentMenuItemId = R.id.navigation_comment;
                        navController.navigate(R.id.navigation_comment);
                        return true;
                    case R.id.navigation_setting:
                        currentMenuItemId = R.id.navigation_setting;
                        navController.navigate(R.id.navigation_setting);
                        return true;
                    case R.id.navigation_add_event:
//                        createNewDialog();DatabaseReference curUserRef =
                        return true;
                }
                return true;
            }
        });



    }

    @Override
    public void onResume() {
        if(isCreated) {
            navController.navigate(R.id.navigation_go);
            currentMenuItemId = R.id.navigation_go;
            isCreated = false;
        }
        super.onResume();
    }


    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(CURRENT_USER, currentUserName);
        super.onSaveInstanceState(savedInstanceState);
    }

    public void addNewEvent(String name, String type) {
       // eventMap.put(new Event(name, type));
    }

    public void addEvent() {

    }

    public HashMap<String, Event> getEventMap(){
        return eventMap;
    }

//  public ArrayList<User> getUserList(){return userList;}
    public String getCurrentUserName(){return this.currentUserName;}

    public ArrayList<ArrayList<Event>> getListofEventLists() {
        if (currentUser != null) {
            //Key is eventID, value is "saved"/"Host"/"past"
            personalEventMap = currentUser.getMyEventList();
        }
        //find a myEventMap<String,Event> of eventID in personalEventMap<String,string> from eventMap<String, Event>
        //key is eventID
        Set<String> eventIDkeySet = personalEventMap.keySet();

        listofEventLists = new ArrayList<>();
        for (int i = 0; i < 5; i++)  {
            listofEventLists.add(new ArrayList<>());
        }

        for (String key : eventIDkeySet) {
            //for all eventstatus, all to the first list
            //list contains all events related to this user
            listofEventLists.get(0).add(eventMap.get(key));

            //if value is "host", add eventobj to first list in listoflists
            if (personalEventMap.get(key).contains("host")) {
                if(eventMap.get(key).isPast()){
                    if(!listofEventLists.get(4).contains(eventMap.get(key))) {
                        listofEventLists.get(4).add(eventMap.get(key));
                    }
                }else{
                    listofEventLists.get(1).add(eventMap.get(key));
                }
            }
            if (personalEventMap.get(key).contains("going")) {
                if(eventMap.get(key).isPast()){
                    if(!listofEventLists.get(4).contains(eventMap.get(key))) {
                        listofEventLists.get(4).add(eventMap.get(key));
                    }
                }else {
                    listofEventLists.get(2).add(eventMap.get(key));
                }
            }
            if (personalEventMap.get(key).contains("saved")) {
                if(eventMap.get(key).isPast()){
                    if(!listofEventLists.get(4).contains(eventMap.get(key))) {
                        listofEventLists.get(4).add(eventMap.get(key));
                    }
                }else {
                    listofEventLists.get(3).add(eventMap.get(key));
                }
            }


        }
        return listofEventLists;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void onCreateEventClick(View view){
        createNewDialog();
    }

    public void createNewDialog(){
        dialogBuilder= new AlertDialog.Builder(this);
        final View EventPopupView = getLayoutInflater().inflate(R.layout.event_popup, null);
        newEventName = (EditText) EventPopupView.findViewById(R.id.newEventName);
        // newEventType = (EditText) EventPopupView.findViewById(R.id.newEventCategory);
        newEventSave = (Button) EventPopupView.findViewById(R.id.btn_cancel_filter);
        newEventCancel = (Button) EventPopupView.findViewById(R.id.btn_apply_filter);

        dialogBuilder.setView(EventPopupView);
        dialog= dialogBuilder.create();
        dialog.show();

        EnumSet<EventType> categories = EnumSet.allOf(EventType.class);

        newEventSpinner = (Spinner) EventPopupView.findViewById(R.id.spinner_category_filter);

        category_list = new ArrayList<>(categories.size());
        category_list.add("Choose A Category");
        for (EventType t: categories) {
            category_list.add(t.toString());
        }

        // add enum values to the arrayList
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, category_list);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        newEventSpinner.setAdapter(dataAdapter);
        newEventSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                newEventType = newEventSpinner.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }

        });
        View mainView = findViewById(R.id.container);


        BottomNavigationView navView = findViewById(R.id.nav_view);
//        Menu menu = navView.getMenu();
//        MenuItem menuItem = menu.findItem(R.id.navigation_menu);

        NavController nacControl = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
//        int currentItem = menuItem.getItemId();

        newEventSave.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if(!newEventName.getText().toString().equals("") && !newEventType.equals("Choose A Category")) {
                    if(newEventName.getText().toString().contains(",") || newEventName.getText().toString().contains(".") ||
                            newEventName.getText().toString().contains("#") ||
                            newEventName.getText().toString().contains("$") ||
                            newEventName.getText().toString().contains("[") ||
                            newEventName.getText().toString().contains("]") ||
                            newEventName.getText().toString().contains("/")){
                        Toast toast = Toast.makeText(getApplicationContext(), "Event name cannot contain the following: \n '/', '.', '#', '$', '[', or ']'", Toast.LENGTH_LONG);
                        toast.show();
                    } else {
                        dialog.dismiss();
                        isCreated = true;

//                    addNewEvent(newEventName.getText().toString(), newEventType);
                        Snackbar.make(mainView, "Event created successfully", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).setAnchorView(navView).show();
                        Intent switchActivityIntent = new Intent(MainActivity.this, CreateEventActivity.class);
                        switchActivityIntent.putExtra("nameTxt", currentUserName);
                        switchActivityIntent.putExtra("eventName", newEventName.getText().toString());
                        switchActivityIntent.putExtra("eventType", newEventType);
                        startActivity(switchActivityIntent);
                    }

                }else if(newEventName.getText().toString().equals("")){
                    Toast toast = Toast.makeText(getApplicationContext(), "Please enter a name for your event.", Toast.LENGTH_LONG);
                    toast.show();
                    //toast.make(view, , Snackbar.LENGTH_LONG)
         //                   .setAction("Action", null).setAnchorView(navView).show();
                }else{
                    Toast toast = Toast.makeText(getApplicationContext(), "Please choose a category for your event.", Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        });

        newEventCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                Snackbar.make(mainView, "Creation canceled, come back later", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).setAnchorView(navView).show();
//                nacControl.navigate(currentMenuItemId);
            }
        });
    }

    public ArrayAdapter<String> getArrayAdapter() {
        //event_list contains all Event objects under this user
        ArrayList<Event> event_list = getListofEventLists().get(0);

        //TO DO: pass event information of this user
        ArrayList<String> myevent_list = new ArrayList<>(event_list.size());
        for (Event e: event_list) {
            String name = e.getEventName();
            myevent_list.add(name);
        }
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, myevent_list);

        return dataAdapter;
    }

    public void verifyPassword(Event event, Intent intent){
        dialogBuilder= new AlertDialog.Builder(this);
        final View EventPopupView = getLayoutInflater().inflate(R.layout.password_dialog, null);
        EditText password = (EditText) EventPopupView.findViewById(R.id.editPassword_dialog);
        // newEventType = (EditText) EventPopupView.findViewById(R.id.newEventCategory);
        Button joinBtn = (Button) EventPopupView.findViewById(R.id.btn_join_passdialog);
        Button cancelBtn = (Button) EventPopupView.findViewById(R.id.btn_cancel_passdialog);

        dialogBuilder.setView(EventPopupView);
        dialog= dialogBuilder.create();
        dialog.show();

        joinBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                String userInputPassword = password.getText().toString();
                if(event.verifyPrivatePassword(userInputPassword)) {
                    dialog.dismiss();
                    Snackbar.make(view, "Password Correct!", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    // add intent
                    //TO DO for Yang: required to pass userList
                    startActivity(intent);

                }else{
                    Snackbar.make(view, "Event password incorrect, please try again later", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                Snackbar.make(view, "Link canceled", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        return super.dispatchTouchEvent(ev);
    }

    public void clickInterest(View view){
        Intent intent = new Intent(this, InterestActivity.class);
        intent.putExtra("nameTxt", currentUserName );
        intent.putExtra("isLogin", isLogin );
        startActivity(intent);
    }

    public void onClickMyHost(View view){
        directFromSetting = true;
        currentMenuItemId = R.id.navigation_go;
        navController.navigate(R.id.navigation_go);
    }
    public Boolean ifRedirectFromSetting(){
        return directFromSetting;
    }

    public void setRedirectFromSetting(){
        directFromSetting = false;
    }

//    public void getAutoComplete(){
//        //eventmap is realtime data from mainactivity
//        int size = eventMap.size();
//        eventNamesAutocomplete = new String[size];
//
//        int i = 0;
//        for(String key: eventMap.keySet()){
//            eventNamesAutocomplete[i] = eventMap.get(key).getEventName();
//            i++;
//        }

//        ArrayAdapter<String> adapter = new ArrayAdapter<String>
//                (this, android.R.layout.simple_spinner_dropdown_item, eventNamesAutocomplete);
//        //Getting the instance of AutoCompleteTextView
//
//
//        AutoCompleteTextView actv = (AutoCompleteTextView) findViewById(R.id.autoSearchTV);
//        actv.setThreshold(1);//will start working from first character
//        actv.setAdapter(adapter);//setting the adapter data into the AutoCompleteTextView
//    }
    public String[] getAutoSearchList(){
        int size = eventMap.size();
        String[] eventNamesAutocomplete = new String[size];

        int i = 0;
        for(String key: eventMap.keySet()){
            eventNamesAutocomplete[i] = eventMap.get(key).getEventName();
            i++;
        }
        return eventNamesAutocomplete;
    }

    public void sendNotification(Event newEvent){
//        LayoutInflater layoutInflater =LayoutInflater.from(this);
//        View v = (View) this.findViewById(R.id.activity)

        String channel = NotiApplication.CHANNEL_1_ID;
        String title = "New Event has been posted";
        String message = "Interested in " + newEvent.getEventName()+"? ";


        Bitmap bigIcon = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                getImageByType(newEvent.getCategory().toString()));

        Notification notification = new NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_action_go_red)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigPictureStyle()
                        .bigPicture(bigIcon)
                        .bigLargeIcon(null))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build();

        notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify(1, notification);
    }

    public int getImageByType(String type){
        int typeImage;
        if(type == null){
            return R.drawable.sticker_education;
        }
        switch(type.toUpperCase()) {
            case "SPORTS":
                typeImage = R.drawable.sticker_sports;
                break;
            case "EDUCATION":
                typeImage = R.drawable.sticker_education;
                break;
            case "FITNESS":
                typeImage = R.drawable.sticker_fitness;
                break;
            case "TECHNOLOGY":
                typeImage = R.drawable.sticker_technology;
                break;
            case "TRAVEL":
                typeImage = R.drawable.sticker_travel;
                break;
            case "OUTDOOR":
                typeImage = R.drawable.sticker_outdoor;
                break;
            case "GAMES":
                typeImage = R.drawable.sticker_games;
                break;
            case "ART":
                typeImage = R.drawable.sticker_art;
                break;
            case "CULTURE":
                typeImage = R.drawable.sticker_culture;
                break;
            case "CAREER":
                typeImage = R.drawable.sticker_career;
                break;
            case "BUSINESS":
                typeImage = R.drawable.sticker_business;
                break;
            case "COMMUNITY":
                typeImage = R.drawable.sticker_community;
                break;
            case "DANCING":
                typeImage = R.drawable.sticker_dancing;
                break;
            case "HEALTH":
                typeImage = R.drawable.sticker_health;
                break;
            case "HOBBIES":
                typeImage = R.drawable.sticker_hobbies;
                break;
            case "MOVEMENT":
                typeImage = R.drawable.sticker_movement;
                break;
            case "LANGUAGE":
                typeImage = R.drawable.sticker_language;
                break;
            case "MUSIC":
                typeImage = (R.drawable.sticker_music);
                break;
            case "FAMILY":
                typeImage = (R.drawable.sticker_family);
                break;
            case "PETS":
                typeImage = (R.drawable.sticker_pets);
                break;
            case "RELIGION":
                typeImage = (R.drawable.sticker_religion);
                break;
            case "SCIENCE":
                typeImage = (R.drawable.sticker_science);
                break;

            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
        return typeImage;
    }

}