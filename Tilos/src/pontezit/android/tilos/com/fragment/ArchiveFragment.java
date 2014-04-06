package pontezit.android.tilos.com.fragment;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

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

    public ArchiveFragment() {
        return;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        LogHelper.Log("ShowDetailsFragment; onActivityCreated run", 1);
        super.onActivityCreated(savedInstanceState);

        getCurrentArchive();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_archive, container, false);
        return view;
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


}
