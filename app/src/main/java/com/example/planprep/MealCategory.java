package com.example.planprep;

public class MealCategory {
    private String strCategory;
    private String strCategoryThumb;
    private String strCategoryDescription;

    public MealCategory(String strCategory, String strCategoryThumb, String strCategoryDescription) {
        this.strCategory = strCategory;
        this.strCategoryThumb = strCategoryThumb;
        this.strCategoryDescription = strCategoryDescription;
    }

    public String getStrCategory() {
        return strCategory;
    }

    public String getStrCategoryThumb() {
        return strCategoryThumb;
    }

    public String getStrCategoryDescription() {
        return strCategoryDescription;
    }
}