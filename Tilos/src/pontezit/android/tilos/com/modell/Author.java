package pontezit.android.tilos.com.modell;

import android.graphics.Bitmap;

public class Author{

    private int id;

    private String name;

    private String alias;

    private String photo;

    private String avatar;

    private Bitmap avatarBitmap;

    public Author(){
    }

    public int getId(){
        return id;
    }

    public void setId(int id){
        this.id = id;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getAlias(){
        return alias;
    }

    public void setAlias(String alias){
        this.alias = alias;
    }

    public String getPhoto(){
        return photo;
    }

    public void setPhoto(String photo){
        this.photo = photo;
    }

    public String getAvatar(){
        return avatar;
    }

    public void setAvatar(String avatar){
        this.avatar = avatar;
    }

    public Bitmap getAvatarBitmap(){
        return avatarBitmap;
    }

    public void setAvatarBitmap(Bitmap avatarBitmap){
        this.avatarBitmap = avatarBitmap;
    }
}
