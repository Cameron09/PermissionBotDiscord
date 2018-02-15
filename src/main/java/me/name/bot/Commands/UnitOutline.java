package me.name.bot.Commands;

import com.jagrosh.jdautilities.commandclient.Command;
import com.jagrosh.jdautilities.commandclient.CommandEvent;
import me.name.bot.Common.EnrolmentHelper;
import me.name.bot.Common.JSONLoad;
import me.name.bot.Models.Configuration;
import me.name.bot.Models.Unit;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import org.apache.commons.lang3.text.WordUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.Year;
import java.util.Arrays;

public class UnitOutline extends Command {
    private final String SAVE_DIRECTORY = "output/unitOutlines/";
    private final long RETAIN_TIME = 2592000000L; //30 days in ms

    public UnitOutline() {
        this.name = "unitoutline";
        this.arguments = "<item>";
        this.aliases = new String[]{"uo"};
        this.help = "This command fetches the unit outline from the given argument OR Discord chat";
    }

    @Override
    protected void execute(CommandEvent event) {
        String[] args = event.getArgs().split("\\s+"); //split by space
        EnrolmentHelper.logUserMessage(event);

        Unit foundUnit;
        Unit[] units = JSONLoad.LoadJSON("data/units.json", Unit[].class);   //load JSON

        String arg = args[0].trim().toLowerCase();

        boolean change = false;
        if(arg == null || arg.isEmpty()) {
            String name = event.getChannel().getName();
            foundUnit = Arrays.stream(units).filter(x -> Arrays.stream(x.getAbbreviation()).anyMatch(z -> z.equalsIgnoreCase(name))).findFirst().orElse(null);
            if(foundUnit != null) {
                String unitcode = foundUnit.getUnitCode();
                change = checkUnit(unitcode, event, foundUnit);
            }
            else {
                event.replyInDm("Unit code/name needs to start with a letter.");
            }
        }
        else if(arg.equals("update")) {
            try {
                String arg2 = args[1].trim().toLowerCase();
                foundUnit = Arrays.stream(units).filter(x -> Arrays.stream(x.getAbbreviation()).anyMatch(z -> z.equalsIgnoreCase(arg2))
                        || x.getFullName().equalsIgnoreCase(arg2) || x.getUnitCode().equalsIgnoreCase(arg2)).findFirst().orElse(null);
                if (foundUnit != null) {
                    String unitcode = foundUnit.getUnitCode();
                    change = updateFileArchive(unitcode, event, foundUnit);
                } else {
                    EnrolmentHelper.giveErrorMessage(arg, event);
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                String name = event.getChannel().getName();
                foundUnit = Arrays.stream(units).filter(x -> Arrays.stream(x.getAbbreviation()).anyMatch(z -> z.equalsIgnoreCase(name))).findFirst().orElse(null);
                if (foundUnit != null) {
                    String unitcode = foundUnit.getUnitCode();
                    change = updateFileArchive(unitcode, event, foundUnit);
                } else {
                    event.replyInDm("Unit code/name needs to start with a letter.");
                }
            }
        }
        else {
            foundUnit = Arrays.stream(units).filter(x -> Arrays.stream(x.getAbbreviation()).anyMatch(z -> z.equalsIgnoreCase(arg))
                    || x.getFullName().equalsIgnoreCase(arg) || x.getUnitCode().equalsIgnoreCase(arg)).findFirst().orElse(null);
            if (foundUnit != null) {
                String unitcode = foundUnit.getUnitCode();
                change = checkUnit(unitcode, event, foundUnit);
            }
            else {
                EnrolmentHelper.giveErrorMessage(arg, event);
            }
        }
        EnrolmentHelper.displayLookupStatus(change, event);
    }

    private boolean checkUnit(String unitcode, CommandEvent event, Unit foundUnit) {
        boolean successful;
        if(isFileArchived(unitcode)) {
            Message message = new MessageBuilder().append("Unit outline for " + WordUtils.capitalize(foundUnit.getFullName()) + ": ").build();
            event.getChannel().sendFile(new File(SAVE_DIRECTORY  + unitcode.toUpperCase() + ".pdf"), message).queue();
            successful = true;
        }
        else {
            FirefoxDriver driver = loginCurtin();
            downloadUnitOutline(unitcode, driver);

            Message message = new MessageBuilder().append("Unit outline for " + WordUtils.capitalize(foundUnit.getFullName()) + ": ").build();
            event.getChannel().sendFile(new File(SAVE_DIRECTORY  + unitcode.toUpperCase() + ".pdf"), message).queue();
            successful = true;
        }
        return(successful);
    }

    private boolean updateFileArchive(String unitcode, CommandEvent event, Unit foundUnit) {
        FirefoxDriver driver = loginCurtin();
        downloadUnitOutline(unitcode, driver);
        return true;
    }

    private FirefoxDriver loginCurtin() {
        System.setProperty("webdriver.gecko.driver", "E:\\Libraries\\Downloads\\geckodriver.exe");
        Configuration config = JSONLoad.LoadJSON("data/config.json", Configuration.class);

        FirefoxDriver driver = new FirefoxDriver();
        driver.get("https://ctl.curtin.edu.au/teaching_learning_services/unit_outline_builder/search_published_UO.cfm");
        WebElement id = driver.findElement(By.xpath("//input[@name='username']"));
        id.sendKeys(config.getStudentID());
        WebElement pw = driver.findElement(By.xpath("//input[@name='password']"));
        pw.sendKeys(config.getStudentPassword());
        WebElement button = driver.findElement(By.xpath("//input[@name='loginSubmit']"));
        button.click();

        WebDriverWait wdw = new WebDriverWait(driver, 30, 500);
        wdw.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[@name='unitCode']")));
        return driver;
    }

    private void downloadUnitOutline(String unitcode, FirefoxDriver driver) {
        WebElement unit = driver.findElement(By.xpath("//input[@name='unitCode']"));
        unit.sendKeys(unitcode);
        WebElement button2 = driver.findElement(By.xpath("//input[@name='next']"));
        button2.click();

        int currYear = Year.now().getValue();
        WebDriverWait wdw = new WebDriverWait(driver, 30, 500);
        wdw.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("thead")));

        int rowCount = driver.findElements(By.xpath("//table[@class='fullwidth']/tbody/tr")).size();
        // int colCount = driver.findElements(By.xpath("//table[@class='fullwidth']/thead/tr/th")).size();

        int maxYear = 0;
        boolean exactMatch = false;
        WebElement linkElement = driver.findElement(By.xpath("//table[@class='fullwidth']/tbody/tr[2]/td[5]"));
        for (int ii = 1; ii <= rowCount; ii++) {
            if (driver.findElement(By.xpath("//table[@class='fullwidth']/tbody/tr[" + ii + "]/td[3]")).getText().equals("Bentley Campus")) {
                WebElement yearElement = driver.findElement(By.xpath("//table[@class='fullwidth']/tbody/tr[" + ii + "]/td[5]"));
                int year = Integer.parseInt(yearElement.getText());
                if (exactMatch) { }
                else if (year - currYear == 0) {
                    exactMatch = true;
                    linkElement = driver.findElement(By.xpath("//table[@class='fullwidth']/tbody/tr[" + ii + "]/td[2]"));
                }
                else {
                    if (year > maxYear) {
                        maxYear = year;
                        linkElement = driver.findElement(By.xpath("//table[@class='fullwidth']/tbody/tr[" + ii + "]/td[2]/a"));
                    }
                }
            }
        }

        String link = linkElement.getAttribute("href");
        driver.close();

        try {
            Files.createDirectory(Paths.get(SAVE_DIRECTORY));
        }
        catch (IOException e) { }

        try {
            URL url = new URL(link);
            InputStream in = url.openStream();
            Files.copy(in, Paths.get(SAVE_DIRECTORY  + unitcode.toUpperCase() + ".pdf"), StandardCopyOption.REPLACE_EXISTING);
            in.close();
        }
        catch (MalformedURLException e) { }
        catch (IOException e) { }
    }

    private boolean isFileArchived(String unitcode) {
        boolean fileArchive = false;
        File file = new File(SAVE_DIRECTORY + unitcode.toUpperCase() + ".pdf");
        long fileDate = file.lastModified();
        long currDate = Instant.now().toEpochMilli();
        if(currDate - fileDate < RETAIN_TIME) {
            fileArchive = true;
        }
        return fileArchive;
    }
}
