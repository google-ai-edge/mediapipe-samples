package com.example.adminpanelapp;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class DashboardActivity extends AppCompatActivity {

    private ListView userListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        userListView = findViewById(R.id.user_list);

        // Create a sample list of users
        ArrayList<String> userList = new ArrayList<>();
        userList.add("User 1: user1@example.com");
        userList.add("User 2: user2@example.com");
        userList.add("User 3: user3@example.com");
        userList.add("User 4: user4@example.com");
        userList.add("User 5: user5@example.com");


        // Create an adapter to display the user list
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, userList);

        userListView.setAdapter(adapter);
    }
}