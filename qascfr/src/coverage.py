# To add a new cell, type '# %%'
# To add a new markdown cell, type '# %% [markdown]'
# %%
import networkx as nx
import csv
import pandas as pd
from tqdm import tqdm
from concurrent.futures import ProcessPoolExecutor, as_completed


# %%
sample = [("Which has a unique water vascular system?", "Echinoids"),
("What is the transmission of genes?", "Reproduction"),
("What is used to preserve food?", "oven"),
("What is the process by which living things give rise to offspring?", "sex"),
("Where does a starfish have its water vascular system?", "in its arms"),
("what kind of beads are formed from vapor condensing?", "h2o"),
("what are frightened by noise?", "babies"),
("What breaks food into nutrients for the body?", "Something that tapeworms do not have"),
("What  weathers rocks?", "water"),
("Most mollusks have what?", "protective bony armor"),
("Sea stars use a unique water vascular system with what?", "feet"),
("What is an example of an echinoderm?", "starfish"),
("Where do beads of water come from?", "Vapor turning into a liquid"),
("What can cause rocks to break down?", "Water"),
("Some invertebrates may have which feature?", "shell"),
("What phylum do starfish belong to?", "Echinoderm."),
("The digestive system breaks food down into what for the body?", "fuel"),
("what do the second-largest invertebrate group have?", "shells"),
("What can be removed to preserve food?", "water"),
("what looks at long-term averages in an area over a long period of time?", "climate"),
("Where do platypus females construct their homes for egg laying?", "soft soil"),
("Beef jerky is what?", "preserved"),
("What gives rise to offspring?", "sex"),
("What could cause an animal to startle?", "Firecrackers"),
("What occurs when rocks are weathered mechanically?", "Sediment"),
("The body needs the digestive system to do what?", "Break down food"),
("Vapor doing what forms beads of liquid?", "condensing"),
("Mussels have what?", "a shell"),
("What forms beads of water? ", "Steam."),
("What comes from reproduction?", "children"),
("How do living things have children?", "reproduction"),
("What enables the body to grow?", "the digestive system"),
("Reproduction is the process by which living things what?", "give birth to babies"),
("The process by which genes are passed is", "reproduction"),
("What depends on the climate in an area?", "bloom time"),
("What does loud noises often do to mammals?", "startle them"),
("what kind of beads are formed by their vapor condensing?", "h2o"),
("what have a unique water vascular system with tube feet?", "sea urchins"),
("What can shells protect?", "soft bodies"),
("Where is water likely to form beads?", "on cold surfaces"),
("what lays their eggs in a burrow?", "monotremes"),
("Slow cooking food in an oven will cause it to be what?", "preserved"),
("What can prevent food spoilage?", "dehydrating food"),
("The average weather in an area during an era is called:", "climate"),
("What does not normally lay eggs?", "Mammal"),
("What type of water formation is formed by clouds?", "beads"),
("The stomach does what in the body?", "breaks food into nutrients"),
("Mechanical weathering produces", "Sediment"),
("What can startle animals?", "engines"),
("what can loud noises cause animals to do?", "snort"),
("What can have a water vascular system with tube feet?", "deuterostomes"),
("what does the digestive system use to produce nutrients for the body?", "catabolism"),
("loud noises can cause hamsters to deliver quite a what?", "bite"),
("What do mollusks contain?", "Calcium carbonate"),
("What has a water vascular system with tube feet?", "blastoids"),
("What is when rocks are broken down?", "Physical weathering"),
("Where do platypus females lay their eggs?", "ground"),
("What kind of animals has a water vascular system with tubed feet?", "starfish"),
("what does the digestive system break food into for the body?", "iron"),
("What is the distinguishing feature of monotremes?", "They lay eggs"),
("what can animals be startled by?", "movement"),
("What might a loud noise in the woods make an animal do?", "snort"),
("What can cause animals to become dangerous?", "Loud noises"),
("Flat spots on rail car wheels can cause what to startle?", "animals"),
("What is formed when rocks break down?", "detritus"),
("What happens to the heat energy during condensation.", "It goes to the remaining air molecules"),
("Removing what from food will preserve it?", "moisture"),
("What does the digestive system break into nutrients for the body?", "meat"),
("What does digestion absorb?", "food"),
("What do echidna lay?", "eggs"),
("a connection is between the eye and what type of  feet in echinoderms", "tube"),
("What have shells?", "most cephalopods"),
("What are two ways you can save food?", "Dehydration and salting"),
("what reproduces to give rise to offspring?", "plants"),
("climate is the average what over a long period of time?", "Earth's atmosphere circulation"),
("What lays their eggs in a burrow?", "some mammals"),
("Climate can be annalyzed with", "satellites"),
("To learn more about the average weather, it is essential to:", "observe it"),
("Climate is the average of things like what in an area over a long period of time", "rain or sun shine"),
("Beads of water are formed when?", "during the chilling season"),
("what females nest in a burrow and wait for the hatching?", "platypus"),
("How do platypus lay eggs?", "in a dug out area"),
("What is needed for the body to grow and remain healthy?", "The digestive system"),
("What is used to preserve food?", "Something from Nesco"),
("Heat and pain may be felt on the skin because of what?", "nerves"),
("Most of what type of animal is known for having a shell?", "snail"),
("What does salting food do to it?", "Preserves it"),
("what can break down rocks?", "ice wedging"),
("Which organ helps break down food into nutrients for our bodies?", "pancreas"),
("What provides the regeneration of cells for the body?", "the digestive system"),
("What is it called when rocks are broken down mechanically?", "erosion"),
("What kind of feet do echinoids have?", "tube"),
("Most soft-bodied invertebrates have what?", "shells"),
("What is the process by which living things give rise to offspring?", "sex"),
("How do echinoderms use their feet to locomote themselves?", "a hydraulic system"),
("What is the average weather in a place over time?", "climate"),
("What are broken down by water?", "rocks"),
("what cause loud noises to animals", "Gunshots"),
("Which of the following  has the most antioxidant benefits for the body?", "preserved blueberries"),
("what usually has a shell?", "oysters")
]


#%%
# Read the codes
with open('../nx_codes.txt') as f:
    reader = csv.reader(f, delimiter='\t')
    codes = {val:code for code, val in reader}

# %%
# Read the edges
edges = set()
with open("../nx_edges_uniq.txt") as f:
    r = csv.reader(f, delimiter='\t')
    for s, e in tqdm(r, desc='reading edges'):
        edges.add(frozenset((int(s), int(e))))
# G = nx.read_edgelist("../nx_edges_uniq.txt", delimiter='\t', nodetype=int)

#%%
# Add them to the graph
G = nx.Graph()
G.add_edges_from(edges)
# %%
# Now read the nodes and add the auxiliary data
with open('../nx_nodes.txt') as f:
    reader = csv.reader(f, delimiter='\t')
    for node, is_question in reader:
        node = int(node)
        if node in G.nodes:
            G.nodes[node]['question'] = is_question == 'true'


# %%
def find_number_of_paths(question, answer):
    # First, create a view that filters out the questions, except the important one
    def x(n):
        if 'question' in G.nodes[n]:
            is_question = G.nodes[n]['question']
            return (not is_question) or n == question
        else:
            return True

    V = nx.subgraph_view(G, filter_node=x)

    question = codes[question]
    answer = codes[answer]

    simple_paths = nx.all_simple_paths(V, question, answer, cutoff=3)
    

    return len(list(simple_paths))


# %%
find_number_of_paths("What type of water formation is formed by clouds?", "beads")


# %%
with ProcessPoolExecutor(max_workers=8) as ctx:
    futures = {ctx.submit(find_number_of_paths, q, a):(q, a) for  q, a in sample}
    results = dict()
    for future in as_completed(futures):
        q, a = futures[future]
        try:
            r = future.result()
            results[(q, a)] = r
            print(r)
        except Exception as ex:
            print(f"Exception on {q},  {a}: {type(ex)}")

print(results.values())


# %%
# with open('noise_levels.json','wb') as f:
#     import pickle
#     pickle.dump(results, f)


# %%



