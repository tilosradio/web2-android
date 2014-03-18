package pontezit.android.tilos.com.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.commonsware.cwac.merge.MergeAdapter;

import java.util.ArrayList;

import flexjson.JSONDeserializer;
import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.MediaPlayerActivity;
import pontezit.android.tilos.com.adapter.ShowListAdapter;
import pontezit.android.tilos.com.modell.Show;
import pontezit.android.tilos.com.utils.Finals;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.PreferencesHelper;

public class ShowListFragment extends Fragment{

    TextView errorTextView;
    ListView listView;
    ProgressBar progressBar;
    private int index = -1;
    private int top = 0;
    private View view;
    private MediaPlayerActivity activity;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LogHelper.Log("MediaPlayerFragment; onCreateView running", 1);

        try{
            setHasOptionsMenu(true);
        }catch(NullPointerException e){
            LogHelper.Log("setHasOptionsMenu, NullpointerExcepotion");
        }
        activity = (MediaPlayerActivity) getActivity();

        if(!activity.isTabletView())
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        view = inflater.inflate(R.layout.fragment_show_list, container, false);
        listView = (ListView) view.findViewById(R.id.showList);
        errorTextView = (TextView) view.findViewById(R.id.showListError);
        progressBar = (ProgressBar) view.findViewById(R.id.showListProgressBar);
        getShowList();


        return view;


    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        LogHelper.Log("ShowListFragment, onActivityCreated run;", 1);
        if (index == -1 && savedInstanceState != null) {
            index = savedInstanceState.getInt("index", -1);
            top = savedInstanceState.getInt("top", 0);
            LogHelper.Log("ShowListFragment, onActivityCreated; savedInstanceState not null; index = " + index + ", top = " + top, 1);

        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        LogHelper.Log("ShowListFragment, onSaveInstanceState run;", 1);
        try{
            index = listView.getFirstVisiblePosition();
            LogHelper.Log("ShowListFragment, onSaveInstanceState; index = " + index, 1);
            View v = listView.getChildAt(0);
            top = (v == null) ? 0 : v.getTop();
            outState.putInt("index", index);
            outState.putInt("top", top);
        }
        catch(Throwable t){
            t.printStackTrace();
        }

    }

    @Override
    public void onPause() {
        LogHelper.Log("ShowListFragment, onPause", 1);
        index = listView.getFirstVisiblePosition();
        View v = listView.getChildAt(0);
        top = (v == null) ? 0 : v.getTop();
        super.onPause();
    }


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


    public void getShowList(){
        String url = Finals.API_BASE_URL + "show";

        AQuery aq = new AQuery(getActivity());
        long expire = 12 * 60 * 60 * 1000; //12 hours cache intervall
        aq.ajax(url, String.class, expire, new AjaxCallback<String>(){

            @Override
            public void callback(String url, String showListString, AjaxStatus status){
                if(showListString != null){
                    ArrayList<Show> showList = new JSONDeserializer<ArrayList<Show>>().use(null, ArrayList.class)
                            .use("values", Show.class)
                            .deserialize(showListString);

                    ArrayList<Show> favoriteShows = new ArrayList<Show>();
                    ArrayList<Show> otherShows = new ArrayList<Show>();

                    int count = 0;
                    PreferencesHelper preferencesHelper = new PreferencesHelper(Finals.PREFS_FAVORITES, getActivity());
                    for(Show show : showList){
                        if(preferencesHelper.sp.getBoolean(show.getId()+"", false))
                            favoriteShows.add(show);
                        else
                            otherShows.add(show);

                        count++;
                    }

                    ShowListAdapter favoriteAdapter = new ShowListAdapter(getActivity(), favoriteShows);
                    ShowListAdapter otherAdapter = new ShowListAdapter(getActivity(), otherShows);

                    LayoutInflater inflateer = LayoutInflater.from(getActivity());
                    //LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
                    //View header = inflateer.inflate(R.layout.merge_adapter_header, true);
                    View favoriteHeaderView = View.inflate(getActivity(), R.layout.merge_adapter_header, null);
                    TextView favoriteTitle = (TextView) favoriteHeaderView.findViewById(R.id.headerTitle);
                    favoriteTitle.setText(getActivity().getResources().getString(R.string.favorite_yours).toUpperCase());

                    View otherHeaderView = View.inflate(getActivity(), R.layout.merge_adapter_header, null);
                    TextView otherTitle = (TextView) otherHeaderView.findViewById(R.id.headerTitle);
                    otherTitle.setText(getActivity().getResources().getString(R.string.other_shows).toUpperCase());

                    MergeAdapter adapter = new MergeAdapter();
                    adapter.addView(favoriteHeaderView);
                    if(favoriteShows.size() == 0){
                        View noShow = View.inflate(getActivity(), R.layout.row_no_show, null);
                        adapter.addView(noShow);
                    }else{
                        adapter.addAdapter(favoriteAdapter);
                    }
                    adapter.addView(otherHeaderView);
                    adapter.addAdapter(otherAdapter);

                    listView.setAdapter(adapter);
                    progressBar.setVisibility(View.GONE);
                    errorTextView.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);

                    if(index != -1){
                        listView.setSelectionFromTop(index, top);
                    }
                }else{
                    errorTextView.setVisibility(View.VISIBLE);
                }
            }
        });

    }

}
