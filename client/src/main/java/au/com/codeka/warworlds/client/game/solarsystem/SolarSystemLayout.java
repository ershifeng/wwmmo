package au.com.codeka.warworlds.client.game.solarsystem;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import au.com.codeka.warworlds.client.MainActivity;
import au.com.codeka.warworlds.client.R;
import au.com.codeka.warworlds.client.game.fleets.FleetListSimple;
import au.com.codeka.warworlds.client.game.world.StarManager;
import au.com.codeka.warworlds.client.util.RomanNumeralFormatter;
import au.com.codeka.warworlds.common.Log;
import au.com.codeka.warworlds.common.Vector2;
import au.com.codeka.warworlds.common.proto.Planet;
import au.com.codeka.warworlds.common.proto.Star;
import javax.annotation.Nonnull;

/**
 * The layout for the {@link SolarSystemScreen}.
 */
// TODO: it probably makes sense to split these into a bunch of sub-views.
public class SolarSystemLayout extends DrawerLayout {
  public interface Callbacks {
    void onBuildClick(int planetIndex);
    void onFocusClick(int planetIndex);
    void onSitrepClick();
    void onViewColonyClick(int planetIndex);
    void onFleetClick(long fleetId);
  }

  private static final Log log = new Log("SolarSystemLayout");

  private final View drawer;
  private final ActionBarDrawerToggle drawerToggle;
  private final SunAndPlanetsView sunAndPlanets;
  private final CongenialityView congeniality;
  private final PlanetSummaryView planetSummary;
  private final StoreView store;
  private final TextView planetName;
  private final FleetListSimple fleetList;

  private final StarSearchListAdapter searchListAdapter;

  @Nonnull private Star star;
  private int planetIndex;

  /**
   * Constructs a new {@link SolarSystemLayout}.
   *
   * @param context The {@link Context}.
   * @param star The {@link Star} to display initially.
   * @param planetIndex The index of the planet to have initially select (or -1 for no planet).
   */
  public SolarSystemLayout(
      Context context, Callbacks callbacks, @Nonnull Star star, int startPlanetIndex) {
    super(context);
    inflate(context, R.layout.solarsystem, this);

    this.star = star;
    this.planetIndex = startPlanetIndex;

    drawer = findViewById(R.id.drawer);
    sunAndPlanets = findViewById(R.id.solarsystem_view);
    congeniality = findViewById(R.id.congeniality);
    store = findViewById(R.id.store);
    planetSummary = findViewById(R.id.planet_summary);
    planetName = findViewById(R.id.planet_name);
    fleetList = findViewById(R.id.fleet_list);

    sunAndPlanets.setPlanetSelectedHandler(planet -> {
      if (planet == null) {
        SolarSystemLayout.this.planetIndex = -1;
      } else {
        SolarSystemLayout.this.planetIndex = planet.index;
      }
      refreshSelectedPlanet();
    });

    planetSummary.setCallbacks(() -> callbacks.onViewColonyClick(planetIndex));

    final Button buildButton = findViewById(R.id.solarsystem_colony_build);
    final Button focusButton = findViewById(R.id.solarsystem_colony_focus);
    final Button sitrepButton = findViewById(R.id.sitrep_btn);
    final Button planetViewButton = findViewById(R.id.enemy_empire_view);
    buildButton.setOnClickListener(v -> callbacks.onBuildClick(planetIndex));
    focusButton.setOnClickListener(v -> callbacks.onFocusClick(planetIndex));
    sitrepButton.setOnClickListener(v -> callbacks.onSitrepClick());
    planetViewButton.setOnClickListener(v -> callbacks.onViewColonyClick(planetIndex));
    fleetList.setFleetSelectedHandler(fleet -> callbacks.onFleetClick(fleet.id));

    ListView searchList = findViewById(R.id.search_result);
    searchListAdapter = new StarSearchListAdapter(
        LayoutInflater.from(getContext()));
    searchList.setAdapter(searchListAdapter);

    searchList.setOnItemClickListener((parent, v, position, id) -> {
      Star s = (Star) searchListAdapter.getItem(position);
      if (s != null) {
        refreshStar(s);
      }
    });

    drawerToggle =
        new ActionBarDrawerToggle(
            (MainActivity) context,
            this,
            R.string.drawer_open,
            R.string.drawer_close) {
          @Override
          public void onDrawerClosed(View view) {
            super.onDrawerClosed(view);
            refreshTitle();
          }

          @Override
          public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            refreshTitle();
            searchListAdapter.setCursor(StarManager.i.getMyStars());
          }
        };
    addDrawerListener(drawerToggle);

    refreshStar(star);
  }

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    drawerToggle.syncState();

    ActionBar actionBar = checkNotNull(getMainActivity().getSupportActionBar());
    actionBar.show();
    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setHomeButtonEnabled(true);
  }

  public void refreshStar(Star star) {
    searchListAdapter.addToLastStars(star);
    fleetList.setStar(star);
    sunAndPlanets.setStar(star);
    store.setStar(star);

    if (planetIndex >= 0) {
      planetName.setText(this.star.name);
    }

    if (planetIndex >= 0) {
      log.debug("Selecting planet #%d", planetIndex);
      sunAndPlanets.selectPlanet(planetIndex);
    } else {
      log.debug("No planet selected");
    }
  }

  // TODO: this is pretty hacky...
  private MainActivity getMainActivity() {
    return (MainActivity) getContext();
  }

  private void refreshTitle() {
    ActionBar actionBar = checkNotNull(getMainActivity().getSupportActionBar());
    log.debug("Refreshing title. isDrawerOpen=%s star=%s actionBar.isShowing=%s",
        isDrawerOpen(drawer) ? "true" : "false",
        star.name,
        actionBar.isShowing() ? "true" : "false");

    if (isDrawerOpen(drawer)) {
      actionBar.setTitle("Star Search");
    } else {
      actionBar.setTitle(star.name);
    }
  }

  private void refreshSelectedPlanet() {
    if (planetIndex < 0) {
      return;
    }
    Planet planet = star.planets.get(planetIndex);

    Vector2 planetCentre = sunAndPlanets.getPlanetCentre(planet);

    String name = star.name + " " + RomanNumeralFormatter.format(star.planets.indexOf(planet) + 1);
    planetName.setText(name);

    if (planetCentre == null) {
      // this is probably because the SolarSystemView probably hasn't rendered yet. We'll
      // just ignore this then cause it'll fire an onPlanetSelected when it finishes
      // drawing.
      congeniality.setVisibility(View.GONE);
    } else {
      float pixelScale = getContext().getResources().getDisplayMetrics().density;
      double x = planetCentre.x;
      double y = planetCentre.y;

      // hard-coded size of the congeniality container: 85x64 dp
      float offsetX = (85 + 20) * pixelScale;
      float offsetY = (64 + 20) * pixelScale;

      if (x - offsetX < 0) {
        offsetX  = -(20 * pixelScale);
      }
      if (y - offsetY < 20) {
        offsetY = -(20 * pixelScale);
      }

      RelativeLayout.LayoutParams params =
          (RelativeLayout.LayoutParams) congeniality.getLayoutParams();
      params.leftMargin = (int) (x - offsetX);
      params.topMargin = (int) (y - offsetY);
      if (params.topMargin < (40 * pixelScale)) {
        params.topMargin = (int)(40 * pixelScale);
      }

      congeniality.setLayoutParams(params);
      congeniality.setVisibility(View.VISIBLE);
    }
    congeniality.setPlanet(planet);

    planetSummary.setPlanet(star, planet);
  }
}
