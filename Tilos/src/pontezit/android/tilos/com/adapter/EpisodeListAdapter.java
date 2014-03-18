package pontezit.android.tilos.com.adapter;

import android.content.Context;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.MediaPlayerActivity;
import pontezit.android.tilos.com.fragment.MediaPlayerFragment;
import pontezit.android.tilos.com.modell.Episode;
import pontezit.android.tilos.com.utils.ArchiveUrl;
import pontezit.android.tilos.com.utils.LogHelper;

public class EpisodeListAdapter extends BaseAdapter{

    private MediaPlayerActivity activity;
    private ArrayList<Episode> showList;
    private static LayoutInflater inflater=null;

    public EpisodeListAdapter(MediaPlayerActivity activity, ArrayList<Episode> showList) {
        this.activity = activity;
        this.showList = showList;
        inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public int getCount() {
        return showList.size();
    }

    public Object getItem(int position) {
        return position;
    }

    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder {
        int url;
        TextView title;
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;

        if(convertView == null){
            holder = new ViewHolder();
            convertView = inflater.inflate(R.layout.row_episode, null);
            holder.title = (TextView) convertView.findViewById(R.id.episodeListTitle); // title
            convertView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){

                    ViewHolder clickedHolder = (ViewHolder) v.getTag();
                    MediaPlayerActivity activity = (MediaPlayerActivity) v.getContext();

                    String startUrl = ArchiveUrl.getUrl(clickedHolder.url);
                    LogHelper.Log("EpisodeListAdapter, onClickListener; startUrl: " + startUrl);
                    activity.stop();
                    activity.processUri(startUrl);
                    if(!activity.isTabletView()){
                        FragmentManager fm = ((FragmentActivity) v.getContext()).getSupportFragmentManager();
                        FragmentTransaction ft = fm.beginTransaction();
                        if (fm.getBackStackEntryCount() > 0) {
                            fm.popBackStack();
                            fm.popBackStack();
                            ft.commit();
                        }
                    }else
                        ((FragmentActivity) v.getContext()).getSupportFragmentManager().popBackStackImmediate();


                }
            });

            convertView.setTag(holder);

        }else{
            holder = (ViewHolder) convertView.getTag();
        }

        Episode episode = showList.get(position);

        // Setting all values in listview
        holder.url = episode.getPlannedFrom();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        holder.title.setText(dateFormat.format(episode.getPlannedFromDate()));
        return convertView;
    }


}
