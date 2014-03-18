package pontezit.android.tilos.com.adapter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxStatus;
import com.androidquery.callback.BitmapAjaxCallback;

import java.util.ArrayList;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.modell.Contributor;
import pontezit.android.tilos.com.utils.StreamDrawable;

public class ContributorAdapter extends BaseAdapter{
    private static final int CORNER_RADIUS = 24; // dips
    private static final int MARGIN = 12; // dips

    private final int mCornerRadius;
    private final int mMargin;
    private final LayoutInflater inflater;
    private ArrayList<Contributor> contributorList;
    private Activity activity;

    public ContributorAdapter(Activity activity, ArrayList<Contributor> contributorList){
        this.contributorList = contributorList;
        this.activity = activity;
        final float density = activity.getResources().getDisplayMetrics().density;
        mCornerRadius = (int) (CORNER_RADIUS * density + 0.5f);
        mMargin = (int) (MARGIN * density + 0.5f);

        inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public int getCount() {
        return contributorList.size();
    }

    public Object getItem(int position) {
        return position;
    }

    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewGroup view = null;

        if (convertView == null) {
            view = (ViewGroup) inflater.inflate(R.layout.row_contributor, null);
        } else {
            view = (ViewGroup) convertView;
        }



        ImageView avatar = (ImageView) view.findViewById(R.id.avatarImage);
        Contributor contributor = contributorList.get(position);
        AQuery aq = new AQuery(activity);
        aq.id(avatar).image(contributor.getAuthor().getAvatar(), true, true, 0, 0, new BitmapAjaxCallback(){

            @Override
            public void callback(String url, ImageView iv, Bitmap bm, AjaxStatus status){
                StreamDrawable d = new StreamDrawable(bm, 100, 0);
                iv.getLayoutParams().width = bm.getWidth();
                iv.getLayoutParams().height = bm.getHeight();
                iv.setBackgroundDrawable(d);
            }

        });



        //ContributorDrawable d = new ContributorDrawable(contributor.getAuthor().getAvatarBitmap(), mCornerRadius, mMargin);
        //view.setBackground(d);


        ((TextView) view.findViewById(R.id.nickView)).setText(contributor.getNick());


        int w = 100;
        int h = 100;

        float ratio = w / (float) h;

        //LayoutParams lp = view.getLayoutParams();
        //lp.width = activity.getResources().getDisplayMetrics().widthPixels;
       //lp.height = (int) (lp.width / ratio);

        return view;
    }
}
