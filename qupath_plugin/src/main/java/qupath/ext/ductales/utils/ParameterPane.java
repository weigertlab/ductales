package qupath.ext.ductales.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import qupath.lib.gui.dialogs.Dialogs;

public class ParameterPane extends GridPane {
	private final static Logger logger = LoggerFactory.getLogger(ParameterPane.class);

	private Map<String, Object> params = new HashMap<>();
	private JsonObject cachedParams = null;
	private List<Runnable> resetParamsRunnables = new ArrayList<>();

	public ParameterPane() {
		this(true);
	}

	public ParameterPane(boolean use_cache_file) {
		super();

		if(use_cache_file) {
			read_parameter_cache_file();
		}

		this.setPadding(new Insets(10));
		this.setHgap(10);
		this.setVgap(10);
	}

	public Map<String, Object> getParameters(){
		return params;
	}

	public void saveParametersInCache() {
		write_parameter_cache_file();
	}

	public void addIntegerTextField(String id, String labelText, int defaultValue) {
		var currentValue = defaultValue;
		if(cachedParams != null && cachedParams.has(id)) {
			currentValue = cachedParams.get(id).getAsInt();
		}

		var label = new Label(labelText);
		this.add(label, 0, this.getRowCount());
		var textField = new TextField(Integer.toString(currentValue));
		textField.textProperty().addListener((obs, oldValue, newValue) -> {
			if(newValue.equals("")) {
				textField.setText(Integer.toString(defaultValue));
				return;
			}
			if (!newValue.matches("\\d*")) {
				textField.setText(newValue.replaceAll("[^\\d]", ""));
			}else {
				try {
					params.put(id, Integer.parseInt(newValue));
				}catch(Exception e){
					textField.setText("");
				}
			}
		});
		this.add(textField, 1, this.getRowCount()-1);
		
		resetParamsRunnables.add(new Runnable() {
			@Override
			public void run() {
				textField.setText(Integer.toString(defaultValue));
			}
		});
		
		params.put(id, currentValue);
	}

	public void addDoubleTextField(String id, String labelText, double defaultValue) {
		var currentValue = defaultValue;
		if(cachedParams != null && cachedParams.has(id)) {
			currentValue = cachedParams.get(id).getAsDouble();
		}

		var label = new Label(labelText);
		this.add(label, 0, this.getRowCount());
		var textField = new TextField(Double.toString(currentValue));
		textField.textProperty().addListener((obs, oldValue, newValue) -> {
			if(newValue.equals("")) {
				textField.setText(Double.toString(defaultValue));
				return;
			}
			if (!newValue.matches("\\d+\\.?\\d*")) {
				newValue = newValue.replaceAll("[^\\d\\.]", ""); // Delete invalid characters
				newValue = newValue.replaceAll("^\\.", "0\\."); // Add a 0 if start with a point
				newValue = newValue.replaceAll("(.*\\..*)\\.", "$1"); // Delete additional points
				textField.setText(newValue);
			}else {
				try {
					params.put(id, Double.parseDouble(newValue));
				}catch(Exception e){
					textField.setText("");
				}
			}
		});
		this.add(textField, 1, this.getRowCount()-1);

		resetParamsRunnables.add(new Runnable() {
			@Override
			public void run() {
				textField.setText(Double.toString(defaultValue));
			}
		});

		params.put(id, currentValue);
	}

	public void addSelectionComboBox(String id, String labelText, int defaultSelectedIndex, String[] possibleValues) {
		var currentSelection = defaultSelectedIndex;
		if(cachedParams != null && cachedParams.has(id)) {
			var cachedSelection = cachedParams.get(id).getAsInt();
			if(cachedSelection < possibleValues.length)
				currentSelection = cachedSelection;
		}

		var label = new Label(labelText);
		this.add(label, 0, this.getRowCount());
		ObservableList<String> items = FXCollections.observableArrayList(possibleValues);
		var comboBox = new ComboBox<String>(items);
		comboBox.setMaxWidth(Double.MAX_VALUE);
		comboBox.getSelectionModel().select(currentSelection);
		comboBox.getSelectionModel().selectedIndexProperty().addListener((obs, oldValue, newValue) -> {
			params.put(id, newValue);
		});
		this.add(comboBox, 1, this.getRowCount()-1);

		resetParamsRunnables.add(new Runnable() {
			@Override
			public void run() {
				comboBox.getSelectionModel().select(defaultSelectedIndex);
			}
		});

		params.put(id, currentSelection);
	}

	public void addMultiSelectionComboBox(String id, String labelText, int[] defaultSelectedIndices, String[] possibleValues) {
		var currentSelection = defaultSelectedIndices;
		if(cachedParams != null && cachedParams.has(id)) {
			var cachedSelection = cachedParams.get(id).getAsJsonArray();
			currentSelection = new int[cachedSelection.size()];
			for(int i = 0; i < cachedSelection.size(); ++i) {
				currentSelection[i] = cachedSelection.get(i).getAsInt();
			}
		}

		var label = new Label(labelText);
		this.add(label, 0, this.getRowCount());
		ObservableList<String> items = FXCollections.observableArrayList(possibleValues);
		var checkComboBox = new CheckComboBox<String>(items);
		checkComboBox.setMaxWidth(Double.MAX_VALUE);
		checkComboBox.getCheckModel().checkIndices(currentSelection);
		checkComboBox.getCheckModel().getCheckedItems().addListener(new ListChangeListener<String>() {
			@Override
			public void onChanged(ListChangeListener.Change<? extends String> c) {
				var checkedIndices = checkComboBox.getCheckModel().getCheckedIndices();
				params.put(id, checkedIndices.toArray(new Integer[checkedIndices.size()]));
			}
		});
		this.add(checkComboBox, 1, this.getRowCount()-1);

		resetParamsRunnables.add(new Runnable() {
			@Override
			public void run() {
				checkComboBox.getCheckModel().checkIndices(defaultSelectedIndices);
			}
		});

		params.put(id, currentSelection);
	}

	public void addFileSelector(String id, String labelText, String defaultValue) {
		addFileSelector(id, labelText, defaultValue, null);
	}
	
	public void addFileSelector(String id, String labelText, String defaultValue, String possibleFileDescription, String... possibleFileExtension) {
		var currentValue = defaultValue;
		if(cachedParams != null && cachedParams.has(id)) {
			currentValue = cachedParams.get(id).getAsString();
		}

		var label = new Label(labelText);
		this.add(label, 0, this.getRowCount());
		var fileSelectorPane = new GridPane();
		fileSelectorPane.setHgap(10);
		var textField = new TextField(currentValue);
		textField.textProperty().addListener((obs, oldValue, newValue) -> {
			if(newValue.equals("")) {
				textField.setText(defaultValue);
				return;
			}
			params.put(id, newValue);
		});
		fileSelectorPane.add(textField, 0, 0);
		var browseButton = new Button("Browse");
		browseButton.setOnMouseClicked(e -> {
			var curFile = new File(textField.getText());
			var file = Dialogs.promptForFile("Select a file", curFile.getParentFile(), possibleFileDescription, possibleFileExtension);
			if(file != null) {
				textField.setText(file.getAbsolutePath());
			}
		});
		fileSelectorPane.add(browseButton, 1, 0);
		this.add(fileSelectorPane, 1, this.getRowCount()-1);

		resetParamsRunnables.add(new Runnable() {
			@Override
			public void run() {
				textField.setText(defaultValue);
			}
		});

		params.put(id, currentValue);
	}

	public void addCheckbox(String id, String labelText, boolean defaultValue) {
		var currentValue = defaultValue;
		if(cachedParams != null && cachedParams.has(id)) {
			currentValue = cachedParams.get(id).getAsBoolean();
		}

		var label = new Label(labelText);
		this.add(label, 0, this.getRowCount());
		var checkBox = new CheckBox();
		checkBox.setIndeterminate(false);
		checkBox.setSelected(currentValue);
		checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
			params.put(id, newValue);
		});
		this.add(checkBox, 1, this.getRowCount()-1);

		resetParamsRunnables.add(new Runnable() {
			@Override
			public void run() {
				checkBox.setSelected(defaultValue);
			}
		});

		params.put(id, currentValue);
	}
	

    static class StringListCell extends ListCell<String> {
        VBox vbox = new VBox();
        Label label = new Label("");
        Pane pane = new Pane();
        Button button = new Button("Delete");

        public StringListCell() {
            super();

            vbox.getChildren().addAll(label, pane, button);
            VBox.setVgrow(pane, Priority.ALWAYS);
            button.setOnAction(event -> getListView().getItems().remove(getItem()));
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(null);

            if (item != null && !empty) {
                label.setText(item);
                setGraphic(vbox);
            }
        }
    }

	public void addStringListCreator(String id, String labelText, String[] defaultValue) {
		var currentValue = defaultValue;
		if(cachedParams != null && cachedParams.has(id)) {
			var cachedValue = cachedParams.get(id).getAsJsonArray();
			currentValue = new String[cachedValue.size()];
			for(int i = 0; i < cachedValue.size(); ++i) {
				currentValue[i] = cachedValue.get(i).getAsString();
			}
		}
		
		var label = new Label(labelText);
		this.add(label, 0, this.getRowCount());

		var vbox = new VBox();
		vbox.setSpacing(10);
		ObservableList<String> items = FXCollections.observableArrayList(currentValue);
		ListView<String> listView = new ListView<>(items);
		listView.setPrefHeight(55);
		listView.setOrientation(Orientation.HORIZONTAL);
		listView.setCellFactory(param -> new StringListCell());

		items.addListener(new ListChangeListener<String>() {
			@Override
			public void onChanged(ListChangeListener.Change<? extends String> c) {
				params.put(id, items.toArray(new String[items.size()]));
			}
		});
		vbox.getChildren().add(listView);
		var hbox = new HBox();
		hbox.setSpacing(10);

		var textField = new TextField();
		hbox.getChildren().add(textField);
        HBox.setHgrow(textField, Priority.ALWAYS);
		var button = new Button("Add");
		button.setOnMouseClicked(e -> {
				var newClass = textField.getText();
				textField.setText("");
				textField.requestFocus();
				items.add(newClass);
			});
		hbox.getChildren().add(button);
		vbox.getChildren().add(hbox);
		this.add(vbox, 1, this.getRowCount()-1);

		resetParamsRunnables.add(new Runnable() {
			@Override
			public void run() {
				items.setAll(defaultValue);
			}
		});

		params.put(id, currentValue);
	}

    static class DoubleListCell extends ListCell<Double> {
        VBox vbox = new VBox();
        Label label = new Label("");
        Pane pane = new Pane();
        Button button = new Button("Delete");

        public DoubleListCell() {
            super();

            vbox.getChildren().addAll(label, pane, button);
            VBox.setVgrow(pane, Priority.ALWAYS);
            button.setOnAction(event -> getListView().getItems().remove(getItem()));
        }

        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(null);

            if (item != null && !empty) {
                label.setText(Double.toString(item));
                setGraphic(vbox);
            }
        }
    }

	public void addDoubleListCreator(String id, String labelText, double[] defaultValue) {
		var currentValue = defaultValue;
		if(cachedParams != null && cachedParams.has(id)) {
			var cachedValue = cachedParams.get(id).getAsJsonArray();
			currentValue = new double[cachedValue.size()];
			for(int i = 0; i < cachedValue.size(); ++i) {
				currentValue[i] = cachedValue.get(i).getAsDouble();
			}
		}
		Arrays.sort(currentValue);
		
		var label = new Label(labelText);
		this.add(label, 0, this.getRowCount());

		var vbox = new VBox();
		vbox.setSpacing(10);
		var curValue = DoubleStream.of(currentValue).boxed().collect(Collectors.toList());
		ObservableList<Double> items = FXCollections.observableArrayList(curValue);
		ListView<Double> listView = new ListView<>(items);
		listView.setPrefHeight(55);
		listView.setOrientation(Orientation.HORIZONTAL);
		listView.setCellFactory(param -> new DoubleListCell());

		items.addListener(new ListChangeListener<Double>() {
			@Override
			public void onChanged(ListChangeListener.Change<? extends Double> c) {
				var res = Stream.of(items.toArray(new Double[items.size()])).mapToDouble(Double::doubleValue).toArray();
				params.put(id, res);
			}
		});
		vbox.getChildren().add(listView);
		var hbox = new HBox();
		hbox.setSpacing(10);

		var textField = new TextField();
		textField.textProperty().addListener((obs, oldValue, newValue) -> {
			if(newValue.equals("")) {
				textField.setText(Double.toString(0));
				return;
			}
			if (!newValue.matches("\\d+\\.?\\d*")) {
				newValue = newValue.replaceAll("[^\\d\\.]", ""); // Delete invalid characters
				newValue = newValue.replaceAll("^\\.", "0\\."); // Add a 0 if start with a point
				newValue = newValue.replaceAll("(.*\\..*)\\.", "$1"); // Delete additional points
				textField.setText(newValue);
			}else {
				try {
					Double.parseDouble(newValue);
				}catch(Exception e){
					textField.setText("");
				}
			}
		});
		hbox.getChildren().add(textField);
        HBox.setHgrow(textField, Priority.ALWAYS);
		var button = new Button("Add");
		button.setOnMouseClicked(e -> {
				var newClass = Double.parseDouble(textField.getText());
				textField.setText("");
				textField.requestFocus();
				items.add(newClass);
				items.sort(new Comparator<Double>() {
					@Override
					public int compare(Double d1, Double d2) {
						return Double.compare(d1, d2);
					}
				});
			});
		hbox.getChildren().add(button);
		vbox.getChildren().add(hbox);
		this.add(vbox, 1, this.getRowCount()-1);

		resetParamsRunnables.add(new Runnable() {
			@Override
			public void run() {
				var defValue = DoubleStream.of(defaultValue).boxed().collect(Collectors.toList());
				items.setAll(defValue);
			}
		});

		params.put(id, currentValue);
	}

	public void addSeparator() {
		addSeparator("");
	}

	public void addSeparator(String text) {
		var separationPane = new GridPane();
		separationPane.setHgap(10);
		var separator1 = new Separator();
		separationPane.add(separator1, 0, 0);
		GridPane.setHgrow(separator1, Priority.ALWAYS);
		if(text != "") {
			var label = new Label(text);
			separationPane.add(label, 1, 0);
			var separator2 = new Separator();
			separationPane.add(separator2, 2, 0);
			GridPane.setHgrow(separator2, Priority.ALWAYS);
		}
		this.add(separationPane, 0, this.getRowCount(), 2, 1);
	}

	public void addButton(String buttonText, EventHandler<? super MouseEvent> onClickEvent) {
		addButton(buttonText, onClickEvent, false);
	}
	
	public void addButton(String buttonText, EventHandler<? super MouseEvent> onClickEvent, boolean addResetDefaultButton) {
		var button = new Button(buttonText);
		button.setOnMouseClicked(onClickEvent);
		var hbox = new HBox();
		hbox.setSpacing(10);
		if(addResetDefaultButton) {
			var resetButton = new Button("Reset default");
			resetButton.setOnMouseClicked(e -> {
				for (var runnable : resetParamsRunnables) {
					runnable.run();
				}
			});
			hbox.getChildren().add(resetButton);
		}
		hbox.getChildren().add(button);
		hbox.setAlignment(Pos.CENTER);
		this.add(hbox, 0, this.getRowCount(), 2, 1);
	}

	private void read_parameter_cache_file() {
		try {
			File cache_file = new File(DuctalesConstants.PARAMETERS_CACHE_FILE);
			if(!cache_file.exists()) return;

			var reader = new FileReader(cache_file);
			cachedParams = JsonParser.parseReader(reader).getAsJsonObject();
			reader.close();
		}catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	private void write_parameter_cache_file() {
		try {
			File cache_file = new File(DuctalesConstants.PARAMETERS_CACHE_FILE);
			FileWriter writer = new FileWriter(cache_file);
			
			var jsonParams = new Gson().toJsonTree(params).getAsJsonObject();
			if(cachedParams != null) {
				for (var cachedParam : cachedParams.entrySet()) {
					// If cached param was not overwritten keep it
					if(!jsonParams.has(cachedParam.getKey())) {
						jsonParams.add(cachedParam.getKey(), cachedParam.getValue());
					}
				}
			}
			
			new Gson().toJson(jsonParams, writer);
			writer.close();
		} catch (IOException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}
}
