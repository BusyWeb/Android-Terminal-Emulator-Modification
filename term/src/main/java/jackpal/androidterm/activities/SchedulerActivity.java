package jackpal.androidterm.activities;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;

import jackpal.androidterm.R;
import jackpal.androidterm.services.SchedulerService;
import jackpal.androidterm.util.GeneralHelper;

/**
 * Created by BusyWeb on 5/20/2017.
 */

public class SchedulerActivity extends AppCompatActivity {

    private Activity mActivity;
    private Context mContext;

    LinearLayout rootView;
    RecyclerView recyclerView;
    SwipeRefreshLayout refresher;
    LinearLayoutManager layoutManager;
    SchedulerAdapter adapter;

    ArrayList<SchedulerService.SchedulerData> schedulerDataList;

    // edit
    ScrollView editRoot;
    EditText editName, editHour, editMinute, editCommand;
    CheckBox checkEnabled;
    Button buttonUpdate, buttonCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scheduler_activity);

        mActivity = this;
        mContext = this;

        GeneralHelper.CheckAndCreateAppFolders();

        prepareApp();
    }

    private void prepareApp() {
        try {
            rootView = (LinearLayout) findViewById(R.id.layoutRoot);
            recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
            layoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false);
            recyclerView.setLayoutManager(layoutManager);

            refresher = (SwipeRefreshLayout) findViewById(R.id.refresher);
            refresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadSchedulers();
                }
            });

            loadSchedulers();

            editRoot = (ScrollView) findViewById(R.id.scrollViewEdit);
            editName = (EditText) findViewById(R.id.editTextName);
            editCommand = (EditText) findViewById(R.id.editTextCommand);
            editHour = (EditText) findViewById(R.id.editTextHour);
            editMinute = (EditText) findViewById(R.id.editTextMinute);
            checkEnabled = (CheckBox) findViewById(R.id.checkBoxEnabled);
            buttonCancel = (Button) findViewById(R.id.buttonCancel);
            buttonUpdate = (Button) findViewById(R.id.buttonUpdate);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.scheduler, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add_scheduler) {
            addSchedule();
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onStart() {
        super.onStart();
        try {

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        try {

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void finish() {
        try {
            finalize();
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            super.finish();
        }
    }

    private void addSchedule() {
        try {
            // make template for user to prevent error
            SchedulerService.SchedulerData data = SchedulerService.getInstance().new SchedulerData();
            data.Data = "ls";
            Date date = new Date();
            date.setHours(date.getHours() == 0 ? 23 : date.getHours() - 2);
            String timeString = GeneralHelper.DateToTimeString(date);
            data.Name = "New schedule: " + timeString + ":" + String.valueOf(date.getSeconds());
            data.TimeOfDay = GeneralHelper.TimeStringToDate(timeString);
            schedulerDataList.add(data);
            GeneralHelper.SaveSchedulerData(schedulerDataList);

            loadSchedulers();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void loadSchedulers() {
        try {
            schedulerDataList = GeneralHelper.LoadSchedulerData();

            refresher = (SwipeRefreshLayout) findViewById(R.id.refresher);
            refresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadSchedulers();
                }
            });
            adapter= new SchedulerAdapter(schedulerDataList);
            recyclerView.setAdapter(adapter);
            refresher.setRefreshing(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class SchedulerViewHolder extends RecyclerView.ViewHolder {

        public int Position;
        public TextView Name;
        public TextView Enabled;
        public TextView Data;
        public TextView TimeOfDay;
        public Button DeleteButton;
        public Button EditButton;

        public SchedulerViewHolder(View itemView) {
            super(itemView);
            Name = (TextView) itemView.findViewById(R.id.textViewItemName);
            Enabled = (TextView) itemView.findViewById(R.id.textViewEnabled);
            Data = (TextView) itemView.findViewById(R.id.textViewItemData);
            TimeOfDay = (TextView) itemView.findViewById(R.id.textViewTimeOfDay);
            DeleteButton = (Button) itemView.findViewById(R.id.buttonDelete);
            EditButton = (Button) itemView.findViewById(R.id.buttonEdit);
        }

        public void SetPosition(int position) {
            this.Position = position;
        }
    }

    public class SchedulerAdapter extends RecyclerView.Adapter<SchedulerViewHolder> {

        ArrayList<SchedulerService.SchedulerData> mSchedulers;

        public SchedulerAdapter(ArrayList<SchedulerService.SchedulerData> schedulers) {
            mSchedulers = schedulers;
        }

        @Override
        public SchedulerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            SchedulerViewHolder holder = null;
            try {
                View view = LayoutInflater.from(mContext).inflate(R.layout.cardview_scheduler_list_item, parent, false);
                holder = new SchedulerViewHolder(view);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return holder;
        }

        @Override
        public void onBindViewHolder(SchedulerViewHolder holder, final int position) {
            try {
                if (holder == null) {
                    return;
                }
                holder.SetPosition(position);
                SchedulerService.SchedulerData data = mSchedulers.get(position);

                holder.Name.setText(data.Name);
                holder.Data.setText(data.Data);
                holder.TimeOfDay.setText("Time: " + GeneralHelper.DateToTimeString(data.TimeOfDay));

                if (data.Enabled) {
                    holder.Enabled.setText("Enabled");
                    holder.Enabled.setTextColor(Color.GREEN);
                } else {
                    holder.Enabled.setText("Disabled");
                    holder.Enabled.setTextColor(Color.RED);
                }

                holder.EditButton.setTag(data);
                holder.DeleteButton.setTag(data);

                holder.EditButton.setOnClickListener(editClickListener);
                holder.DeleteButton.setOnClickListener(deleteClickListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            if (mSchedulers == null) {
                return 0;
            } else {
                return mSchedulers.size();
            }
        }
    }

    View.OnClickListener editClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                final SchedulerService.SchedulerData data = (SchedulerService.SchedulerData)v.getTag();
                if (data == null) {
                    return;
                }

                showEditSchedule(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private void showEditSchedule(final SchedulerService.SchedulerData data) {
        try {
            editRoot.setVisibility(View.VISIBLE);

            editName.setText(data.Name);
            checkEnabled.setChecked(data.Enabled);
            editCommand.setText(data.Data);
            editHour.setText(String.valueOf(data.TimeOfDay.getHours()));
            editMinute.setText(String.valueOf(data.TimeOfDay.getMinutes()));
            buttonCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editRoot.setVisibility(View.GONE);
                }
            });
            buttonUpdate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean success = false;
                    try {
                        SchedulerService.SchedulerData updated = SchedulerService.getInstance().new SchedulerData();
                        updated.Name = editName.getText().toString();
                        updated.Enabled = checkEnabled.isChecked();
                        updated.Data = editCommand.getText().toString();
                        Date date = new Date();
                        int hour = Integer.parseInt(editHour.getText().toString());
                        int minute = Integer.parseInt(editMinute.getText().toString());
                        if (hour < 0) {
                            hour = 0;
                        }
                        if (hour >= 24) {
                            hour = 0;
                        }
                        if (minute < 0) {
                            minute = 0;
                        }
                        if (minute >= 60) {
                            minute = 0;
                        }
                        date.setHours(hour);
                        date.setMinutes(minute);
                        date.setYear(2000);
                        date.setMonth(0);
                        date.setDate(1);
                        updated.TimeOfDay = date;
                        if (updated.Name == null || updated.Name.length() < 1
                            || updated.Data == null || updated.Data.length() < 1) {
                            Toast.makeText(mContext, "Please enter all fields, and try again.", Toast.LENGTH_LONG).show();
                        } else {
                            success = updateSchedule(data, updated);
                        }

                        if (success) {
                            editRoot.setVisibility(View.GONE);
                            loadSchedulers();
                        } else {
                            Toast.makeText(mContext, "Failed to update, please try again.", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean updateSchedule(SchedulerService.SchedulerData original, SchedulerService.SchedulerData updated) {
        try {
            for (SchedulerService.SchedulerData item : schedulerDataList) {
                if (GeneralHelper.IsSchedulerDataEquals(item, original)) {
                    schedulerDataList.remove(item);
                    break;
                }
            }
            schedulerDataList.add(updated);
            GeneralHelper.SaveSchedulerData(schedulerDataList);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    View.OnClickListener deleteClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                final SchedulerService.SchedulerData data = (SchedulerService.SchedulerData)v.getTag();
                if (data == null) {
                    return;
                }
                Snackbar.make(rootView,
                        "Delete Scheduler: " + data.Name,
                        Snackbar.LENGTH_LONG)
                        .setActionTextColor(Color.WHITE)
                        .setAction("DELETE", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    if (data == null) {
                                        return;
                                    }
                                    boolean success = false;
                                    success = removeScheduler(data);
                                    if (success) {
//                                        mActivity.runOnUiThread(new Runnable() {
//                                            @Override
//                                            public void run() {
//                                                if (adapter != null) {
//                                                    adapter.notifyDataSetChanged();
//                                                }
//                                            }
//                                        });
                                        schedulerDataList = GeneralHelper.LoadSchedulerData();
                                        loadSchedulers();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private boolean removeScheduler(SchedulerService.SchedulerData data) {
        try {
            for (SchedulerService.SchedulerData item : schedulerDataList) {
                if (GeneralHelper.IsSchedulerDataEquals(item, data)) {
                    schedulerDataList.remove(item);
                    break;
                }
            }
            GeneralHelper.SaveSchedulerData(schedulerDataList);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
