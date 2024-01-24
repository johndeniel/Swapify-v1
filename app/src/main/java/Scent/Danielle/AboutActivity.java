package Scent.Danielle;

// Android core components
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

// AndroidX components
import androidx.fragment.app.Fragment;

public class AboutActivity extends Fragment {

    public AboutActivity() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_about, container, false);
    }
}