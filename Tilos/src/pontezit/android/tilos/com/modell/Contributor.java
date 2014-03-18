package pontezit.android.tilos.com.modell;

public class Contributor{

    String nick;

    Author author;

    public Contributor(){
    }

    public String getNick(){
        return nick;
    }

    public void setNick(String nick){
        this.nick = nick;
    }

    public Author getAuthor(){
        return author;
    }

    public void setAuthor(Author author){
        this.author = author;
    }
}
