package pontezit.android.tilos.com.modell;


import java.net.MalformedURLException;
import java.net.URL;

import pontezit.android.tilos.com.utils.LogHelper;

public class Show{

    private int id;

    private String name;

    private String definition;

    private int type;

    private int status;

    private String alias;

    private String banner;

    public Show(){
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

    public String getDefinition(){
        return definition;
    }

    public void setDefinition(String definition){
        this.definition = definition;
    }

    public int getType(){
        return type;
    }

    public void setType(int type){
        this.type = type;
    }

    public int getStatus(){
        return status;
    }

    public void setStatus(int status){
        this.status = status;
    }

    public String getAlias(){
        return alias;
    }

    public void setAlias(String alias){
        this.alias = alias;
    }

    public String getBanner(){
        return banner;
    }

    public void setBanner(String banner){
        this.banner = banner;
    }
}
