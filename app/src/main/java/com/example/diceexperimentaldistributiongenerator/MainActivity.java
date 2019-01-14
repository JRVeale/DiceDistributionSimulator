package com.example.diceexperimentaldistributiongenerator;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.TreeMap;

import static java.lang.Double.NaN;


public class MainActivity extends AppCompatActivity {

    //Map for storing roll results
    private Map<Integer,Integer> freq_map = new HashMap<Integer,Integer>();
    GraphView graph;
    int trials = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Below defines action for button press
        final Button button = findViewById(R.id.roll_button);
        button.setOnClickListener(new View.OnClickListener(){
            /** Called when the user taps the Roll! button*/
            public void onClick(View v) {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);
                }
                catch(Exception e){}
                if(rollemAndFillMap()) {
                    fillMap();
                }
            }
        });
    }

    private void fillMap() {
        graph = (GraphView) findViewById(R.id.graph);
        graph.removeAllSeries();

        LineGraphSeries<DataPoint> series = new LineGraphSeries<>(new DataPoint[]{});
        graph.addSeries(series);

        //Uncomment to hide
        // graph.getGridLabelRenderer().setVerticalLabelsVisible(false);

        DataPoint[] dataPoints = mapToDataPoints(freq_map);

        int numPoints = 0;

        for (DataPoint pt : dataPoints){
            series.appendData(pt, false,100000,false);
            numPoints++;
        }

        double minX = dataPoints[0].getX();
        double maxX = dataPoints[numPoints-1].getX();
        double rangeX = maxX-minX;

        double minY = 0;
        //double maxY = 1;
        double maxY = Math.min(graph.getViewport().getMaxY(true)*1.2,1);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(minX);
        graph.getViewport().setMaxX(maxX);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(minY);
        graph.getViewport().setMaxY(maxY);


        return;
    }

    private DataPoint[] mapToDataPoints(Map mp){
        //Create an ordered version of this map.
        Map<Integer,Integer> ordered_mp = new TreeMap<>(mp);
        //Double check ordering is working (good)
        TextView textView = findViewById(R.id.raw_results);
        textView.setText(ordered_mp.toString());
        textView.setMovementMethod(new ScrollingMovementMethod());
        /*TEST
        Double temp = 4.5;
        Integer intTemp = temp.intValue();
        textView.setText(intTemp.toString());*/
        //Create an empty array of DataPoints ready to be filled
        DataPoint points[] = new DataPoint[mp.size()];

        //Iterate through ordered Map
        Iterator it = ordered_mp.entrySet().iterator();
        int i = 0;
        while (it.hasNext()){
            Map.Entry pair = (Map.Entry)it.next();
            double xVal = ((Integer) pair.getKey()).doubleValue();
            double yVal = ((Integer) pair.getValue()).doubleValue();
            double yPropVal = yVal/trials;
            //double xVal = (double) pair.getKey();
            //double yVal = (double) pair.getValue();
            points[i] = new DataPoint(xVal,yPropVal);
            i++;
        }

        return points;
    }

    private boolean rollemAndFillMap(){
        //Get user input (dice_func & number of trials)
        EditText diceText = findViewById(R.id.user_dice_input);
        String user_dice_func = diceText.getText().toString();
        if(user_dice_func.equals("")){
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Enter a dice function",
                    Toast.LENGTH_LONG);
            toast.show();
            return false;
        }
        EditText trialsText = findViewById(R.id.user_rolls_input);
        String temp_user_trials = trialsText.getText().toString();

        if (temp_user_trials.equals("")){
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Enter a number of trials to roll",
                    Toast.LENGTH_LONG);
            toast.show();
            return false;
        }
        int user_trials = Integer.parseInt(temp_user_trials);
        if(user_trials > 100000){
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Maximum 10,000 trials!",
                    Toast.LENGTH_LONG);
            toast.show();
            user_trials = 100000;
            trialsText.setText("100000");
        }
        //update class value
        trials = user_trials;

        String tidy_dice_func = getTidyDice(user_dice_func);
        String postfix_dice_func = diceInfixToDicePostfix(tidy_dice_func);
        //Clear freq_map for new graph
        freq_map.clear();
        for (int i = 0; i < user_trials; i++){
            Double trial_result = roll(postfix_dice_func);
            if(trial_result.isNaN()){
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Bad roll format!",
                        Toast.LENGTH_LONG);
                toast.show();
                return false;
            }
            Integer floored_trial_result = trial_result.intValue();
            //if not already in map, add with value 1, else increase value
            int count = freq_map.containsKey(floored_trial_result) ? freq_map.get(floored_trial_result) : 0;
            freq_map.put(floored_trial_result, count + 1);
        }
        return true;
    }

    private String getTidyDice(String input) {
        String temp = input;
        temp = temp.replace(" ","");
        temp = temp.replace("", " ").trim(); //spaces between everything
        temp = temp.replaceAll("\\. (\\d)", "\\.$1");   //fix RHS of split decimal
        temp = temp.replaceAll("(\\d) \\.", "$1\\.");   //fix LHS of split decimal
        temp = temp.replaceAll("(\\d) (\\d)", "$1$2");  //start fix split numbers
        temp = temp.replaceAll("(\\d) (\\d)", "$1$2");  //end fix split numbers
        return temp;
    }

    private String diceInfixToDicePostfix(String infix) {
        /*Shunting Yard algorithm adapted from
        https://rosettacode.org/wiki/Parsing/Shunting-yard_algorithm#Java
        (Thanks!)*/

        //Requires a space separated input (easy to achieve)

        /*Added 'd' as an operator here, allows things like (4+1d8)d6...)
        and 2d6^3 to work as expected*/
        final String ops = "-+/*^^d";
        /* To find out the precedence, we take the index of the
           token in the ops string and divide by 2 (rounding down).
           This will give us: 0, 0, 1, 1, 2, 2, 3
           (^ in twice so that d is most powerful operator after parens*/

        StringBuilder sb = new StringBuilder();
        Stack<Integer> s = new Stack<>();

        for (String token : infix.split("\\s")) {
            if (token.isEmpty())
                continue;
            char c = token.charAt(0);
            int idx = ops.indexOf(c);   //Get priority of operator (or -1 if not operator)

            // check for operator
            if (idx != -1) {
                if (s.isEmpty())
                    s.push(idx);

                else {
                    while (!s.isEmpty()) {
                        int prec2 = s.peek() / 2;
                        int prec1 = idx / 2;
                        if (prec2 > prec1 || (prec2 == prec1 && c != '^'))
                            sb.append(ops.charAt(s.pop())).append(' ');
                        else break;
                    }
                    s.push(idx);
                }
            }
            else if (c == '(') {
                s.push(-2); // -2 stands for '('
            }
            else if (c == ')') {
                // until '(' on stack, pop operators.
                while (s.peek() != -2)
                    sb.append(ops.charAt(s.pop())).append(' ');
                s.pop();
            }
            else {
                sb.append(token).append(' ');
            }
        }
        while (!s.isEmpty())
            sb.append(ops.charAt(s.pop())).append(' ');
        return sb.toString();
    }

    private Double roll(String postfix){

        final String ops = "-+/*^d";

        Stack<Double> stack = new Stack<>();

        //iterate through postfix
            //on number: add to stack
            //on operator: operate on two at top of stack, push result to stack
        //at end: return item left in stack

        try {

            for (String str : postfix.split("\\s")) {
                if (str.isEmpty())
                    continue;   //skip below for gaps
                if (ops.indexOf(str) == -1) {
                    //Is a number, add to stack!
                    stack.push(Double.parseDouble(str));
                } else {
                    //Is an operator, operate!
                    Double second = stack.pop();    //i.e. for "4 5 -" this is 5
                    Double first = stack.pop();     //i.e. for "4 5 -" this is 4
                    if (str.equals("-")) {
                        stack.push(first - second);
                    } else if (str.equals("+")) {
                        stack.push(first + second);
                    } else if (str.equals("/")) {
                        stack.push(first / second);
                    } else if (str.equals("*")) {
                        stack.push(first * second);
                    } else if (str.equals("^")) {
                        stack.push(Math.pow(first, second));
                    } else if (str.equals("d")) {
                        //first is num_dice, second is type_dice
                        int sum = 0;
                        for (int i = 0; i < first.intValue(); i++) {
                            Random rand = new Random();
                            sum = sum + (rand.nextInt(second.intValue()) + 1);
                        }
                        Double sum_db = new Double(sum);
                        stack.push(sum_db);
                    } else {
                        //uhoh!
                    }
                }
            }

            return stack.pop();
        }
        catch(Throwable e){
            return NaN;
        }
    }

}
