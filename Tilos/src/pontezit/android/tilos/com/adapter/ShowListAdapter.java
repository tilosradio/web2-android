package pontezit.android.tilos.com.adapter;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.MediaPlayerActivity;
import pontezit.android.tilos.com.fragment.MediaPlayerFragment;
import pontezit.android.tilos.com.fragment.ShowDetailsFragment;
import pontezit.android.tilos.com.modell.Show;
import pontezit.android.tilos.com.utils.LogHelper;

public class ShowListAdapter extends BaseAdapter{

    private ArrayList<Show> showList;
    private static LayoutInflater inflater=null;

    public ShowListAdapter(Activity activity, ArrayList<Show> showList) {

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
        int showId;
        TextView title, artist;
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;

        if(convertView == null){
            holder = new ViewHolder();
            convertView = inflater.inflate(R.layout.row_show, null);
            holder.title = (TextView) convertView.findViewById(R.id.showListTitle); // title
            holder.artist = (TextView) convertView.findViewById(R.id.showListArtist); // artist name
            convertView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    ViewHolder clickedHolder = (ViewHolder) v.getTag();
                    MediaPlayerActivity activity = (MediaPlayerActivity) v.getContext();
                    FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();
                    Fragment fragment = new ShowDetailsFragment();

                    Bundle bundle = new Bundle();
                    bundle.putInt("showId", clickedHolder.showId);
                    fragment.setArguments(bundle);

                    transaction.setCustomAnimations(R.anim.fragment_in, R.anim.fragment_out);

                    if(activity.isTabletView())
                        transaction.replace(R.id.detailsContainer, fragment);
                    else
                        transaction.replace(R.id.mediaPlayerContainer, fragment);

                    transaction.addToBackStack("showList");
                    transaction.commit();

                }
            });

            convertView.setTag(holder);

        }else{
            holder = (ViewHolder) convertView.getTag();
        }

        Show show = showList.get(position);
        // Setting all values in listview
        holder.showId = show.getId();
        holder.title.setText(show.getName());
        holder.artist.setText(show.getDefinition());
        return convertView;
    }

}