package github.scarsz.wynter.wynter;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) args = new String[]{""};

        try {
            new Wynter(args[0]);
        } catch (Exception e) {
            System.out.println("Failed starting Wynter: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
