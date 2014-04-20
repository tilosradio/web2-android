package pontezit.android.tilos.com.fragment;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

import java.util.Calendar;

import flexjson.JSONDeserializer;
import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.MediaPlayerActivity;
import pontezit.android.tilos.com.adapter.ContributorAdapter;
import pontezit.android.tilos.com.adapter.EpisodeListAdapter;
import pontezit.android.tilos.com.modell.Author;
import pontezit.android.tilos.com.modell.Contributor;
import pontezit.android.tilos.com.modell.Episode;
import pontezit.android.tilos.com.modell.Show;
import pontezit.android.tilos.com.utils.Finals;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.PreferencesHelper;

public class ArchiveFragment extends Fragment {

    private View view;
    private TextView archiveDate;
    private MediaPlayerActivity activity;

    public ArchiveFragment() {
        return;
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try{
            setHasOptionsMenu(true);
        }catch(NullPointerException e){
            LogHelper.Log("setHasOptionsMenu, NullpointerExcepotion");
        }
        activity = (MediaPlayerActivity) getActivity();

        //Show the menubar
        if(!activity.isTabletView())
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_archive, container, false);
        archiveDate = (TextView) view.findViewById(R.id.archiveDate);

        //Set the datepicker for today's date
        setDatePicker();
        //Show today's archive
        getCurrentArchive();

        return view;
    }

    private void setDatePicker(){
        archiveDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();
                Dialog mDialog = new DatePickerDialog(getActivity(),
                        mDatesetListener, calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH), calendar
                        .get(Calendar.DAY_OF_MONTH));
                mDialog.show();
            }
        });
    }


    public void getCurrentArchive(){
        AQuery aq = new AQuery(getActivity());
/*
        String url = Finals.API_BASE_URL + "show/"+showId;
        aq.ajax(url, String.class, new AjaxCallback<String>(){

            @Override
            public void callback(String url, String showDetailsString, AjaxStatus status){
                if(showDetailsString != null){
                    show = new JSONDeserializer<Show>().use(null, Show.class)
                            .use("values.contributors", Contributor.class)
                            .use("values.contributors.author", Author.class)
                            .use("values.episodes", Episode.class)
                            .deserialize(showDetailsString);

                    LogHelper.Log("Elmúlt adások: " + show.getEpisodes().size() + "db", 1);
                    EpisodeListAdapter episodeListAdapter = new EpisodeListAdapter((MediaPlayerActivity) getActivity(), show.getEpisodes());
                    recentEpisodes.setAdapter(episodeListAdapter);
                    progressBar.setVisibility(View.GONE);

                    title.setText(show.getName());
                    title.setVisibility(View.VISIBLE);

                    String schedulingText = "";
                    try{
                        schedulingText = " ("+show.getSchedulingText().get(0)+")";
                    }catch(NullPointerException e){
                        LogHelper.Log("schedulingText nullpointerException", 1);
                    }
                    details.setText(show.getDefinition() + schedulingText);

                    details.setVisibility(View.VISIBLE);

                    ContributorAdapter contributorAdapter = new ContributorAdapter(getActivity(), show.getContributors());
                    gridView.setAdapter(contributorAdapter);
                    isFavorite = preferencesHelper.sp.getBoolean(show.getId()+"", false);
                    setStar();

                }else{
                    LogHelper.Log("getShowDetails error; onFailure; error:");
                }
            }
        });
*/
    }

    private DatePickerDialog.OnDateSetListener mDatesetListener = new DatePickerDialog.OnDateSetListener() {

        public void onDateSet(DatePicker arg0, int arg1, int arg2, int arg3) {
            arg2 = arg2 + 1;

            String my_date = arg1 + "-" + arg2 + "-" + arg3;
            archiveDate.setText(my_date);
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);
        if(!activity.isTabletView())
            menu.clear();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch(item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
        }

        return false;
    }


}
