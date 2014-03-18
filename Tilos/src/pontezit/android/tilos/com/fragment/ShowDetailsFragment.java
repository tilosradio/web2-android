package pontezit.android.tilos.com.fragment;

import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import flexjson.JSONDeserializer;
import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.MediaPlayerActivity;
import pontezit.android.tilos.com.adapter.ContributorAdapter;
import pontezit.android.tilos.com.adapter.EpisodeListAdapter;
import pontezit.android.tilos.com.alarm.Alarm;
import pontezit.android.tilos.com.modell.Author;
import pontezit.android.tilos.com.modell.Contributor;
import pontezit.android.tilos.com.modell.Episode;
import pontezit.android.tilos.com.modell.Show;
import pontezit.android.tilos.com.utils.Finals;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.PreferencesHelper;

public class ShowDetailsFragment extends Fragment{

    private TextView title, details;
    private ProgressBar progressBar;
    private ListView recentEpisodes;
    private int showId;
    private GridView gridView;
    private MenuItem favorite;
    private Show show;
    private boolean isFavorite;
    private PreferencesHelper preferencesHelper;
    private ImageView bg;
    public ShowDetailsFragment(){
        return;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        LogHelper.Log("ShowDetailsFragment; onActivityCreated run", 1);
        super.onActivityCreated(savedInstanceState);

        Bundle bundle = getArguments();
        if(bundle != null){
            showId = bundle.getInt("showId", 0);
            LogHelper.Log("ShowDetailsFragment; onActivityCreated; showId = " + showId, 1);
        }

        preferencesHelper = new PreferencesHelper(Finals.PREFS_FAVORITES, getActivity());
        getShowDetails();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LogHelper.Log("MediaPlayerFragment; onCreateView running", 1);
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_show_details, container, false);
        bg = (ImageView) view.findViewById(R.id.bgDetails);
        if(getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            Picasso.with(getActivity()).load("file:///android_asset/bg_show.jpg").fit().rotate(90).into(bg);
        else
            Picasso.with(getActivity()).load("file:///android_asset/bg_show.jpg").fit().into(bg);

        title = (TextView) view.findViewById(R.id.showDetailsTitle);
        Typeface typeface = Typeface.createFromAsset(getActivity().getAssets(), "title.ttf");

        title.setTypeface(typeface);
        details = (TextView) view.findViewById(R.id.showDetailsDetails);
        recentEpisodes = (ListView ) view.findViewById(R.id.recentEpisodesList);
        gridView = (GridView) view.findViewById(R.id.contributorsGrid);
        progressBar = (ProgressBar) view.findViewById(R.id.showDetailsProgressBar);

        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        bg.setImageBitmap(null);
        bg.destroyDrawingCache();
        System.gc();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.details_menu, menu);
        favorite = menu.findItem(R.id.menu_item_favorite);
        return;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
            case R.id.menu_item_favorite:
                isFavorite = !isFavorite;
                if(isFavorite)
                    Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.favorite_marked), Toast.LENGTH_SHORT).show();
                setStar();
                return true;
        }

        return false;
    }

    public void getShowDetails(){
        LogHelper.Log("getShowDetails; showId = " + showId, 1);
        AQuery aq = new AQuery(getActivity());

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

    }

    private void setStar(){
        if(!isFavorite)
            favorite.setIcon(R.drawable.star_off);
        else
            favorite.setIcon(R.drawable.star_on);

        if(show != null)
            preferencesHelper.putBoolean(show.getId()+"", isFavorite);
    }


}
